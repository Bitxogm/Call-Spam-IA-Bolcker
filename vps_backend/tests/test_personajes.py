"""Personajes: prompts en personas/*.txt, elección al empezar cada llamada.

python3 -m unittest discover -s vps_backend/tests -v
"""
import asyncio
import json
import os
import shutil
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m


class Prompts(unittest.TestCase):
    def test_los_dos_prompts_estan_cargados(self):
        self.assertEqual(set(m.PROMPTS), {'manolo', 'paquita'})
        self.assertTrue(m.PROMPTS['manolo'].startswith('Eres Manolo Ferreiro'))
        self.assertTrue(m.PROMPTS['paquita'].startswith('Eres Paquita Morales'))
        self.assertEqual(m.PROMPT_MANOLO, m.PROMPTS['manolo'])

    def test_paquita_tiene_las_reglas_de_manolo(self):
        for clave in ('manolo', 'paquita'):
            prompt = m.PROMPTS[clave]
            self.assertIn('1-2 frases como mucho', prompt, clave)
            self.assertIn('REGLA ESTRICTA', prompt, clave)
            self.assertIn('NO UNA MÁQUINA', prompt, clave)
            self.assertIn('Máximo 1 pregunta por turno', prompt, clave)

    def test_ninguno_acepta_ni_da_datos(self):
        for clave in ('manolo', 'paquita'):
            prompt = m.PROMPTS[clave]
            self.assertIn('NUNCA ACEPTES NI DES DATOS', prompt, clave)
            self.assertIn('"sí", "acepto", "de acuerdo" ni "vale"', prompt, clave)
            self.assertIn('Ni reales ni inventados', prompt, clave)
            # Va antes de las instrucciones de qué hacer si insiste
            self.assertLess(prompt.index('NUNCA ACEPTES'), prompt.index('SI EL SPAMMER'), clave)

    def test_paquita_no_escribe_el_acento(self):
        self.assertIn('sin imitar el acento por escrito', m.PROMPTS['paquita'])

    def test_falta_manolo_no_arranca(self):
        with tempfile.TemporaryDirectory() as vacio, \
             patch.object(m, 'PERSONAS_DIR', vacio):
            with self.assertRaises(FileNotFoundError):
                m._leer_prompts()

    def test_falta_paquita_se_desactiva(self):
        with tempfile.TemporaryDirectory() as solo_manolo, \
             patch.object(m, 'ari_log'):
            shutil.copy(os.path.join(os.path.dirname(m.__file__), 'personas', 'manolo.txt'), solo_manolo)
            with patch.object(m, 'PERSONAS_DIR', solo_manolo):
                self.assertEqual(set(m._leer_prompts()), {'manolo'})


class Eleccion(unittest.TestCase):
    def test_lectura_del_env(self):
        with patch.object(m, 'ari_log'):
            self.assertEqual(m._leer_personaje(None), 'aleatorio')
            self.assertEqual(m._leer_personaje(' Paquita '), 'paquita')
            self.assertEqual(m._leer_personaje('roberto'), 'aleatorio')   # aún no existe

    def test_aleatorio_elige_entre_los_dos(self):
        with patch.object(m, 'PERSONAJE', 'aleatorio'):
            elegidos = {m.elegir_personaje() for _ in range(200)}
        self.assertEqual(elegidos, {'manolo', 'paquita'})

    def test_fijo(self):
        with patch.object(m, 'PERSONAJE', 'paquita'):
            self.assertEqual({m.elegir_personaje() for _ in range(20)}, {'paquita'})

    def test_sin_fichero_de_paquita_siempre_manolo(self):
        with patch.object(m, 'PERSONAJE', 'paquita'), \
             patch.object(m, 'PROMPTS', {'manolo': 'x'}):
            self.assertEqual(m.elegir_personaje(), 'manolo')

    def test_voces_en_uso(self):
        with patch.object(m, 'TTS_PROVIDER', 'cartesia'):
            with patch.object(m, 'PERSONAJE', 'aleatorio'):
                self.assertEqual(m.voces_en_uso(), {'cartesia', 'elevenlabs'})
            with patch.object(m, 'PERSONAJE', 'manolo'):
                self.assertEqual(m.voces_en_uso(), {'cartesia'})
        with patch.object(m, 'TTS_PROVIDER', 'edge'):
            self.assertEqual(m.voces_en_uso(), set())


class VozDeReserva(unittest.TestCase):
    def test_cada_personaje_la_suya(self):
        with patch.object(m, 'TTS_VOICE', 'gl-ES-RoiNeural'):
            self.assertEqual(m.voz_edge('manolo'), 'gl-ES-RoiNeural')
            self.assertEqual(m.voz_edge('desconocido'), 'gl-ES-RoiNeural')
        self.assertEqual(m.voz_edge('paquita'), 'es-ES-ElviraNeural')   # sin PAQUITA_VOZ_RESERVA

    def test_paquita_configurable_por_env(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('manolo_ari_voz', m.__file__)
        mod = importlib.util.module_from_spec(spec)
        with patch.dict(os.environ, {'PAQUITA_VOZ_RESERVA': 'es-ES-XimenaNeural'}), \
             patch('dotenv.load_dotenv'):
            spec.loader.exec_module(mod)
        self.assertEqual(mod.voz_edge('paquita'), 'es-ES-XimenaNeural')


class EnLaLlamada(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.ruta = os.path.join(self.tmp.name, 'call_log.json')
        self.dichos = []

        async def decir(state, texto):
            self.dichos.append(texto)

        for p in (
            patch.object(m, 'CALL_LOG_PATH', self.ruta),
            patch.object(m, 'ari_log'),
            patch.object(m, 'run_fixed', AsyncMock()),
            patch.object(m, 'play_response', decir),
            patch.object(m.ari, 'answer', AsyncMock()),
            patch.object(m.ari, 'create_bridge', AsyncMock(return_value='bridge-1')),
            patch.object(m.ari, 'add_channel_to_bridge', AsyncMock()),
            patch.object(m.ari, 'create_external_media', AsyncMock(return_value='em-1')),
        ):
            p.start()
            self.addCleanup(p.stop)
        self.addCleanup(setattr, m, 'call_state', None)

    async def entrar(self, modo, personaje):
        with patch.object(m, 'get_current_mode', MagicMock(return_value=modo)), \
             patch.object(m, 'PERSONAJE', personaje):
            await m.on_stasis_start({'channel': {'id': 'canal-1', 'name': 'PJSIP/z-1',
                                                 'caller': {'number': '600000000'}}})
            await asyncio.sleep(0)
        with open(self.ruta) as f:
            return json.load(f)[-1]

    async def test_paquita_queda_en_el_log_y_saluda_ella(self):
        registro = await self.entrar('AI', 'paquita')
        self.assertEqual(registro['personaje'], 'paquita')
        self.assertEqual(m.call_state.personaje, 'paquita')
        self.assertEqual(self.dichos, [m.PERSONAJES['paquita']['saludo']])

    async def test_manolo_saluda_como_siempre(self):
        registro = await self.entrar('AI', 'manolo')
        self.assertEqual(registro['personaje'], 'manolo')
        self.assertIn('no encuentro las gafas', self.dichos[0])

    async def test_modo_2_sin_personaje(self):
        registro = await self.entrar('FIXED', 'paquita')
        self.assertIsNone(registro['personaje'])
        self.assertEqual(self.dichos, [])

    async def test_cada_uno_se_despide_a_su_manera(self):
        state = m.CallState('canal-1')
        state.personaje = 'paquita'
        with patch.object(m, 'play_response', AsyncMock()) as decir, \
             patch.object(m.asyncio, 'sleep', AsyncMock()), \
             patch.object(m.ari, 'hangup', AsyncMock()):
            await m.despedida_y_cierre(state)
            await state.tts_task
        self.assertIn('mi alma', decir.await_args.args[1])


class PromptPorPersonaje(unittest.IsolatedAsyncioTestCase):
    async def test_groq_recibe_el_prompt_del_personaje(self):
        respuesta = SimpleNamespace(choices=[SimpleNamespace(
            message=SimpleNamespace(content='Ea, ¿y eso qué es, hijo?'), finish_reason='stop')])
        create = AsyncMock(return_value=respuesta)
        historial = []
        with patch.object(m.groq_client.chat.completions, 'create', create), \
             patch.object(m, 'ari_log') as log:
            await m.get_manolo_response(historial, 'Hola', 'paquita')
        self.assertEqual(create.await_args.kwargs['messages'][0]['content'], m.PROMPTS['paquita'])
        self.assertIn('Paquita (Groq)', log.call_args.args[0])


if __name__ == '__main__':
    unittest.main()
