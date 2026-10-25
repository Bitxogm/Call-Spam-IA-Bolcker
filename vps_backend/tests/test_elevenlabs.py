"""ElevenLabs como voz de Paquita: misma escalera que Cartesia (Edge de reserva, mensaje fijo).

python3 -m unittest discover -s vps_backend/tests -v
"""
import asyncio
import json
import os
import tempfile
import unittest
from datetime import date
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m

import healthcheck as hc


def respuesta(status=200, content=b'\xff' * 320, text='', datos=None):
    r = MagicMock(status_code=status, content=content, text=text)
    r.json.return_value = datos or {}
    return r


def cliente_http(*respuestas, metodo='post'):
    cliente = MagicMock()
    setattr(cliente, metodo, AsyncMock(side_effect=list(respuestas)))
    contexto = MagicMock()
    contexto.__aenter__ = AsyncMock(return_value=cliente)
    contexto.__aexit__ = AsyncMock(return_value=False)
    return contexto, getattr(cliente, metodo)


class Base(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.logs = []
        for p in (
            patch.object(m, 'ELEVENLABS_STATE_PATH', os.path.join(self.tmp.name, 'elevenlabs_state.json')),
            patch.object(m, 'CARTESIA_STATE_PATH', os.path.join(self.tmp.name, 'cartesia_state.json')),
            patch.object(m, 'CALL_LOG_PATH', os.path.join(self.tmp.name, 'call_log.json')),
            patch.object(m, 'ELEVENLABS_API_KEY', 'sk_el_test'),
            patch.object(m, 'ELEVENLABS_VOICE_ID', 'voz-paquita'),
            patch.object(m, 'TTS_PROVIDER', 'cartesia'),
            patch.object(m, 'ari_log', side_effect=self.logs.append),
        ):
            p.start()
            self.addCleanup(p.stop)


class Peticion(Base):
    async def test_pide_ulaw_8000_con_flash_y_la_voz_de_paquita(self):
        contexto, post = cliente_http(respuesta(content=b'\x7f' * 160))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            audio = await m.synthesize_elevenlabs('Ea, ¿quién es?')
        self.assertEqual(audio, b'\x7f' * 160)          # directo al RTP, sin ffmpeg
        self.assertEqual(post.await_args.args[0],
                         'https://api.elevenlabs.io/v1/text-to-speech/voz-paquita')
        self.assertEqual(post.await_args.kwargs['params'], {'output_format': 'ulaw_8000'})
        self.assertEqual(post.await_args.kwargs['headers'], {'xi-api-key': 'sk_el_test'})
        cuerpo = post.await_args.kwargs['json']
        self.assertEqual((cuerpo['model_id'], cuerpo['language_code']), ('eleven_flash_v2_5', 'es'))
        self.assertEqual(cuerpo['text'], 'Ea, ¿quién es?')

    async def test_sin_clave_es_error_de_tts(self):
        with patch.object(m, 'ELEVENLABS_API_KEY', None):
            with self.assertRaises(m.TTSError):
                await m.synthesize_elevenlabs('Hola')

    async def test_cuota_agotada_es_sin_creditos(self):
        cuerpo = '{"detail": {"status": "quota_exceeded", "message": "This request exceeds your quota"}}'
        contexto, _ = cliente_http(respuesta(401, b'', cuerpo))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            with self.assertRaises(m.ElevenLabsSinCreditos):
                await m.synthesize_elevenlabs('Hola')

    async def test_clave_invalida_no_es_sin_creditos(self):
        contexto, _ = cliente_http(respuesta(401, b'', '{"detail": {"status": "invalid_api_key"}}'))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_elevenlabs('Hola')
        self.assertNotIsInstance(ctx.exception, m.SinCreditos)

    async def test_timeout_corto(self):
        self.assertEqual(m.ELEVENLABS_TIMEOUT, 3)

        async def colgada(_texto):
            await asyncio.sleep(3600)

        with patch.object(m, 'ELEVENLABS_TIMEOUT', 0.05), \
             patch.object(m, '_synthesize_elevenlabs', colgada):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_elevenlabs('Hola')
        self.assertIn('ElevenLabs: timeout', str(ctx.exception))

    def test_dia_de_renovacion_propio(self):
        with patch.object(m, 'ELEVENLABS_DIA_RENOVACION', 20), \
             patch.object(m, 'CARTESIA_DIA_RENOVACION', 1):
            self.assertEqual(m.marcar_elevenlabs_agotado(date(2026, 10, 25)), date(2026, 11, 20))
        self.assertIsNotNone(m.elevenlabs_agotado_hasta(date(2026, 10, 25)))
        self.assertIsNone(m.cartesia_agotado_hasta(date(2026, 10, 25)))   # cada uno el suyo


class Escalera(Base):
    def setUp(self):
        super().setUp()
        self.saltos = []
        self.edge = AsyncMock(return_value=b'edge')
        p = patch.object(m, 'synthesize_edge', self.edge)
        p.start()
        self.addCleanup(p.stop)

    async def sintetizar(self, elevenlabs, cartesia=None):
        with patch.object(m, 'synthesize_elevenlabs', elevenlabs), \
             patch.object(m, 'synthesize_cartesia', cartesia or AsyncMock()):
            return await m.synthesize_mulaw('Hola', voz='elevenlabs',
                                            al_saltar=lambda p, e: self.saltos.append(p))

    async def test_voz_elevenlabs_usa_elevenlabs_y_no_cartesia(self):
        cartesia = AsyncMock()
        self.assertEqual(await self.sintetizar(AsyncMock(return_value=b'el'), cartesia), b'el')
        cartesia.assert_not_called()
        self.edge.assert_not_called()
        self.assertRegex([l for l in self.logs if l.startswith('TTS ')][0],
                         r'^TTS ElevenLabs: \d+ ms \(2 bytes\)$')

    async def test_falla_y_sigue_con_edge(self):
        self.assertEqual(await self.sintetizar(AsyncMock(side_effect=m.TTSError('ElevenLabs 500'))), b'edge')
        self.assertEqual(self.saltos, ['ElevenLabs'])

    async def test_sin_caracteres_marca_su_estado(self):
        await self.sintetizar(AsyncMock(side_effect=m.ElevenLabsSinCreditos('ElevenLabs 401 quota')))
        self.assertIsNotNone(m.elevenlabs_agotado_hasta())
        self.assertIsNone(m.cartesia_agotado_hasta())

    async def test_agotado_ni_lo_intenta(self):
        m.marcar_elevenlabs_agotado()
        elevenlabs = AsyncMock()
        self.assertEqual(await self.sintetizar(elevenlabs), b'edge')
        elevenlabs.assert_not_called()
        self.assertEqual(self.saltos, ['ElevenLabs'])

    async def test_con_tts_provider_edge_todo_va_a_edge(self):
        elevenlabs = AsyncMock()
        with patch.object(m, 'TTS_PROVIDER', 'edge'):
            self.assertEqual(await self.sintetizar(elevenlabs), b'edge')
        elevenlabs.assert_not_called()


class EnUnaLlamadaDePaquita(Base):
    def setUp(self):
        super().setUp()
        self.run_fixed = AsyncMock()
        p = patch.object(m, 'run_fixed', self.run_fixed)
        p.start()
        self.addCleanup(p.stop)
        self.state = m.CallState('canal-1')
        self.state.personaje = 'paquita'
        self.state.transport = MagicMock()
        self.state.remote_addr = ('127.0.0.1', 4000)
        with open(m.CALL_LOG_PATH, 'w') as f:
            json.dump([{'id': 'canal-1', 'modo': 'AI', 'plan_b': None, 'personaje': 'paquita'}], f)

    async def test_un_solo_intento_y_plan_b_elevenlabs(self):
        elevenlabs = AsyncMock(side_effect=m.TTSError('ElevenLabs 500'))
        cartesia = AsyncMock()
        with patch.object(m, 'synthesize_elevenlabs', elevenlabs), \
             patch.object(m, 'synthesize_cartesia', cartesia), \
             patch.object(m, 'synthesize_edge', AsyncMock(return_value=b'\xff' * 160)), \
             patch.object(m.asyncio, 'sleep', AsyncMock()):
            for _ in range(3):
                await m.play_response(self.state, 'Hola')
        await asyncio.sleep(0)
        elevenlabs.assert_awaited_once()
        cartesia.assert_not_called()
        with open(m.CALL_LOG_PATH) as f:
            self.assertEqual(json.load(f)[0]['plan_b'], 'ElevenLabs')
        self.run_fixed.assert_not_called()

    async def test_paquita_cae_a_elvira_y_manolo_a_roi(self):
        edge = AsyncMock(return_value=b'\xff' * 160)
        with patch.object(m, 'synthesize_elevenlabs', AsyncMock(side_effect=m.TTSError('ElevenLabs 500'))), \
             patch.object(m, 'synthesize_cartesia', AsyncMock(side_effect=m.TTSError('Cartesia 500'))), \
             patch.object(m, 'synthesize_edge', edge), \
             patch.object(m, 'TTS_VOICE', 'gl-ES-RoiNeural'), \
             patch.dict(m.PERSONAJES['paquita'], {'voz_edge': 'es-ES-ElviraNeural'}), \
             patch.object(m.asyncio, 'sleep', AsyncMock()):
            await m.play_response(self.state, 'Hola')
            manolo = m.CallState('canal-2')
            manolo.transport = MagicMock()
            await m.play_response(manolo, 'Hola')
        self.assertEqual([c.args[1] for c in edge.await_args_list],
                         ['es-ES-ElviraNeural', 'gl-ES-RoiNeural'])
        self.assertTrue(any('→ Edge (es-ES-ElviraNeural)' in l for l in self.logs))


class HealthcheckElevenLabs(Base):
    def setUp(self):
        super().setUp()
        p = patch.object(hc, 'log')
        p.start()
        self.addCleanup(p.stop)

    def test_las_comprobaciones_siguen_a_los_personajes(self):
        with patch.object(m, 'PERSONAJE', 'aleatorio'):
            checks = hc.construir_checks()
            self.assertIn('ElevenLabs', checks)
            self.assertIn('Cartesia', checks)
        with patch.object(m, 'PERSONAJE', 'manolo'):
            self.assertNotIn('ElevenLabs', hc.construir_checks())
        with patch.object(m, 'PERSONAJE', 'paquita'):
            checks = hc.construir_checks()
            self.assertIn('ElevenLabs: saldo bajo', checks)
            self.assertNotIn('Cartesia', checks)
        with patch.object(m, 'TTS_PROVIDER', 'edge'):
            self.assertNotIn('ElevenLabs', hc.construir_checks())

    async def comprobar(self, usados, limite=10000):
        contexto, get = cliente_http(
            respuesta(datos={'voice_id': 'voz-paquita'}),
            respuesta(datos={'character_count': usados, 'character_limit': limite}),
            metodo='get')
        sintetizar = AsyncMock()
        estado = {}
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto), \
             patch.object(m, 'synthesize_elevenlabs', sintetizar):
            resultado = await hc.run_checks({
                'ElevenLabs': hc.check_elevenlabs_cuenta(estado),
                'ElevenLabs: saldo bajo': hc.check_elevenlabs_saldo(estado),
                'ElevenLabs: sin créditos': hc.check_elevenlabs_creditos(estado),
            })
        sintetizar.assert_not_called()      # nunca gasta caracteres
        rutas = [c.args[0] for c in get.await_args_list]
        self.assertEqual(rutas, ['https://api.elevenlabs.io/v1/voices/voz-paquita',
                                 'https://api.elevenlabs.io/v1/user/subscription'])
        return resultado

    async def test_saldo_suficiente(self):
        self.assertEqual(set((await self.comprobar(5000)).values()), {None})

    async def test_menos_del_20_por_ciento_avisa(self):
        resultado = await self.comprobar(8500)
        self.assertIn('quedan 15%', resultado['ElevenLabs: saldo bajo'])
        self.assertIn('1500 de 10000 caracteres', resultado['ElevenLabs: saldo bajo'])
        self.assertIsNone(resultado['ElevenLabs: sin créditos'])

    async def test_agotado_avisa_de_las_dos_cosas(self):
        resultado = await self.comprobar(10000)
        self.assertIn('quedan 0%', resultado['ElevenLabs: saldo bajo'])
        self.assertIn('sin caracteres', resultado['ElevenLabs: sin créditos'])

    async def test_agotado_por_el_estado_que_dejo_una_llamada(self):
        m.marcar_elevenlabs_agotado()
        resultado = await hc.run_checks({'ElevenLabs: sin créditos': hc.check_elevenlabs_creditos({})})
        self.assertIn('Paquita usa Edge', resultado['ElevenLabs: sin créditos'])

    async def test_si_falla_la_cuenta_el_saldo_no_se_inventa(self):
        contexto, _ = cliente_http(respuesta(401, text='invalid_api_key'), metodo='get')
        estado = {}
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            resultado = await hc.run_checks({
                'ElevenLabs': hc.check_elevenlabs_cuenta(estado),
                'ElevenLabs: saldo bajo': hc.check_elevenlabs_saldo(estado),
            })
        self.assertIn('401', resultado['ElevenLabs'])
        self.assertIsNone(resultado['ElevenLabs: saldo bajo'])


if __name__ == '__main__':
    unittest.main()
