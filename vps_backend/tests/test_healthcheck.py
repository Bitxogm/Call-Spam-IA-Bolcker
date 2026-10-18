"""healthcheck.py: comprobaciones, timeout, emails por cambio de estado.

python3 -m unittest discover -s vps_backend/tests -v
"""
import asyncio
import importlib.util
import json
import os
import tempfile
import unittest
import wave
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m

import healthcheck as hc


class ImportarManoloAri(unittest.TestCase):
    def test_importar_no_arranca_el_servidor(self):
        ruta = os.path.join(os.path.dirname(hc.__file__), 'manolo_ari.py')
        spec = importlib.util.spec_from_file_location('manolo_ari_import_check', ruta)
        modulo = importlib.util.module_from_spec(spec)
        with patch('asyncio.run') as asyncio_run, \
             patch.object(m.websockets, 'connect') as ws_connect:
            spec.loader.exec_module(modulo)
        asyncio_run.assert_not_called()
        ws_connect.assert_not_called()


class Comprobaciones(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        p = patch.object(hc, 'log')
        p.start()
        self.addCleanup(p.stop)

    async def test_tts_usa_la_funcion_de_manolo(self):
        tts = AsyncMock(side_effect=m.TTSError('Edge TTS: 403'))
        with patch.object(m, 'synthesize_mulaw', tts):
            resultado = await hc.run_checks({'TTS': hc.check_tts})
        tts.assert_awaited_once()
        self.assertEqual(resultado, {'TTS': 'Edge TTS: 403'})

    async def test_groq_usa_la_funcion_de_manolo(self):
        groq = AsyncMock(return_value='¿Eh? ¿Quién dices?')
        with patch.object(m, 'generate_manolo_reply', groq):
            resultado = await hc.run_checks({'Groq': hc.check_groq})
        groq.assert_awaited_once()
        self.assertEqual(resultado, {'Groq': None})

    async def test_deepgram_recibe_la_muestra_del_repo(self):
        with wave.open(hc.SAMPLE_PATH, 'rb') as wf:
            esperado = wf.getnframes() * 2
        stt = AsyncMock(return_value='Hola, le llamo de la compañía de la luz.')
        with patch.object(m, 'deepgram_stt', stt):
            resultado = await hc.run_checks({'Deepgram': hc.check_deepgram})
        self.assertEqual(resultado, {'Deepgram': None})
        self.assertEqual(len(stt.await_args.args[0]), esperado)

    async def test_deepgram_vacio_sobre_la_muestra_es_fallo(self):
        with patch.object(m, 'deepgram_stt', AsyncMock(return_value='')):
            resultado = await hc.run_checks({'Deepgram': hc.check_deepgram})
        self.assertIn('vacía', resultado['Deepgram'])

    async def test_servicio_inactivo(self):
        proc = MagicMock()
        proc.communicate = AsyncMock(return_value=(b'inactive\n', None))
        with patch.object(hc.asyncio, 'create_subprocess_exec', AsyncMock(return_value=proc)) as exe:
            resultado = await hc.run_checks({'servicio x': hc.check_service('manolo_ari')})
        self.assertEqual(exe.await_args.args, ('systemctl', 'is-active', 'manolo_ari'))
        self.assertEqual(resultado, {'servicio x': 'estado "inactive"'})

    async def test_servicio_activo(self):
        proc = MagicMock()
        proc.communicate = AsyncMock(return_value=(b'active\n', None))
        with patch.object(hc.asyncio, 'create_subprocess_exec', AsyncMock(return_value=proc)):
            resultado = await hc.run_checks({'servicio x': hc.check_service('asterisk')})
        self.assertEqual(resultado, {'servicio x': None})

    async def test_comprobacion_colgada_no_bloquea_las_demas(self):
        async def colgada():
            await asyncio.sleep(3600)

        async def bien():
            pass

        resultado = await hc.run_checks({'lenta': colgada, 'rápida': bien}, timeout=0.05)
        self.assertEqual(resultado, {'lenta': 'sin respuesta en 0.05s', 'rápida': None})

    def test_estan_todas_las_comprobaciones(self):
        self.assertEqual(set(hc.CHECKS), {
            'TTS', 'Groq', 'Deepgram', 'servicio asterisk',
            'servicio manolo_ari', 'servicio asterisk-control-api',
        })


class Emails(unittest.TestCase):
    def test_sin_cambios_no_hay_email(self):
        self.assertEqual(hc.build_emails({}, {}), [])
        self.assertEqual(hc.build_emails({'Groq': 'a'}, {'Groq': 'b'}), [])

    def test_fallo_nuevo(self):
        emails = hc.build_emails({}, {'Deepgram': '401 Unauthorized'})
        self.assertEqual(len(emails), 1)
        asunto, cuerpo = emails[0]
        self.assertIn('Falla: Deepgram', asunto)
        self.assertIn('401 Unauthorized', cuerpo)
        self.assertNotIn('edge-tts', cuerpo)

    def test_recuperacion(self):
        emails = hc.build_emails({'Groq': 'timeout'}, {})
        self.assertEqual(len(emails), 1)
        self.assertIn('Recuperado: Groq', emails[0][0])

    def test_fallo_y_recuperacion_a_la_vez(self):
        asuntos = [a for a, _ in hc.build_emails({'Groq': 'x'}, {'TTS': 'y'})]
        self.assertEqual(len(asuntos), 2)

    def test_tts_con_edge_sugiere_actualizar(self):
        with patch.object(m, 'TTS_PROVIDER', 'edge'):
            _, cuerpo = hc.build_emails({}, {'TTS': 'Edge TTS: 403'})[0]
        self.assertIn('sudo /root/ai_bridge/venv/bin/pip install -U edge-tts', cuerpo)

    def test_tts_con_otro_proveedor_no_sugiere_edge(self):
        with patch.object(m, 'TTS_PROVIDER', 'azure'):
            _, cuerpo = hc.build_emails({}, {'TTS': 'error'})[0]
        self.assertNotIn('edge-tts', cuerpo)

    def test_envio_por_gmail_con_starttls(self):
        env = {'ALERT_EMAIL_FROM': 'a@gmail.com', 'ALERT_EMAIL_TO': 'b@gmail.com',
               'ALERT_EMAIL_PASSWORD': 'app-password'}
        smtp = MagicMock()
        with patch.dict(os.environ, env), \
             patch.object(hc.smtplib, 'SMTP') as clase:
            clase.return_value.__enter__.return_value = smtp
            hc.send_email('asunto', 'cuerpo')
        clase.assert_called_once_with('smtp.gmail.com', 587, timeout=hc.SMTP_TIMEOUT)
        smtp.starttls.assert_called_once()
        smtp.login.assert_called_once_with('a@gmail.com', 'app-password')
        enviado = smtp.send_message.call_args.args[0]
        self.assertEqual((enviado['To'], enviado['Subject']), ('b@gmail.com', 'asunto'))

    def test_sin_credenciales_no_intenta_enviar(self):
        with patch.dict(os.environ, {}, clear=True), \
             patch.object(hc.smtplib, 'SMTP') as clase:
            with self.assertRaises(RuntimeError):
                hc.send_email('a', 'b')
        clase.assert_not_called()


class CambioDeEstado(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.envios = MagicMock()
        for p in (
            patch.object(hc, 'STATE_PATH', os.path.join(self.dir.name, 'state.json')),
            patch.object(hc, 'send_email', self.envios),
            patch.object(hc, 'log'),
        ):
            p.start()
            self.addCleanup(p.stop)

    async def ejecutar(self, resultados):
        with patch.object(hc, 'run_checks', AsyncMock(return_value=resultados)):
            return await hc.main()

    async def test_fallo_persistente_solo_avisa_una_vez(self):
        self.assertEqual(await self.ejecutar({'TTS': 'Edge TTS: 403', 'Groq': None}), 1)
        self.assertEqual(await self.ejecutar({'TTS': 'Edge TTS: 403', 'Groq': None}), 1)
        self.assertEqual(await self.ejecutar({'TTS': 'Edge TTS: 403', 'Groq': None}), 1)
        self.assertEqual(self.envios.call_count, 1)

        self.assertEqual(await self.ejecutar({'TTS': None, 'Groq': None}), 0)
        self.assertEqual(self.envios.call_count, 2)
        self.assertIn('Recuperado', self.envios.call_args.args[0])

        with open(hc.STATE_PATH) as f:
            self.assertEqual(json.load(f)['fallos'], {})

    async def test_todo_bien_desde_el_principio_no_manda_nada(self):
        self.assertEqual(await self.ejecutar({'TTS': None}), 0)
        self.envios.assert_not_called()

    async def test_si_el_email_falla_reintenta_en_el_siguiente_run(self):
        self.envios.side_effect = [OSError('Network unreachable'), None]
        self.assertEqual(await self.ejecutar({'Groq': 'timeout'}), 2)
        self.assertFalse(os.path.exists(hc.STATE_PATH))
        self.assertEqual(await self.ejecutar({'Groq': 'timeout'}), 1)
        self.assertEqual(self.envios.call_count, 2)


if __name__ == '__main__':
    unittest.main()
