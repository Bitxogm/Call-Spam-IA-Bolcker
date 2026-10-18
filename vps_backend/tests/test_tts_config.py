"""Voz, velocidad y tono del TTS desde el .env (TTS_VOICE, TTS_RATE, TTS_PITCH).

python3 -m unittest discover -s vps_backend/tests -v
"""
import importlib.util
import os
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m

RUTA = os.path.join(os.path.dirname(m.__file__), 'manolo_ari.py')
VARIABLES = ('TTS_VOICE', 'TTS_RATE', 'TTS_PITCH')


def cargar_con_entorno(**env):
    """Carga una copia nueva de manolo_ari con solo estas variables TTS en el entorno."""
    limpio = {k: v for k, v in os.environ.items() if k not in VARIABLES}
    limpio.update(env)
    spec = importlib.util.spec_from_file_location('manolo_ari_tts_config', RUTA)
    modulo = importlib.util.module_from_spec(spec)
    # Sin load_dotenv: en el VPS leería el .env real y el test dependería de él
    with patch.dict(os.environ, limpio, clear=True), patch('dotenv.load_dotenv'):
        spec.loader.exec_module(modulo)
    return modulo


class ConfiguracionTTS(unittest.TestCase):
    def test_sin_variables_usa_los_valores_de_siempre(self):
        mod = cargar_con_entorno()
        self.assertEqual((mod.TTS_VOICE, mod.TTS_RATE, mod.TTS_PITCH),
                         ('es-ES-AlvaroNeural', '-8%', '-4Hz'))

    def test_lee_las_tres_del_entorno(self):
        mod = cargar_con_entorno(TTS_VOICE='gl-ES-RoiNeural', TTS_RATE='-8%', TTS_PITCH='-4Hz')
        self.assertEqual((mod.TTS_VOICE, mod.TTS_RATE, mod.TTS_PITCH),
                         ('gl-ES-RoiNeural', '-8%', '-4Hz'))

    def test_variable_vacia_cae_al_defecto(self):
        mod = cargar_con_entorno(TTS_VOICE='', TTS_RATE='+5%', TTS_PITCH='')
        self.assertEqual((mod.TTS_VOICE, mod.TTS_RATE, mod.TTS_PITCH),
                         ('es-ES-AlvaroNeural', '+5%', '-4Hz'))


class SintesisUsaLaConfiguracion(unittest.IsolatedAsyncioTestCase):
    async def test_edge_tts_recibe_voz_velocidad_y_tono(self):
        communicate = MagicMock()
        communicate.save = AsyncMock()
        proc = MagicMock(returncode=0)
        proc.communicate = AsyncMock(return_value=(b'\xff' * 160, b''))
        with patch.object(m, 'TTS_VOICE', 'gl-ES-RoiNeural'), \
             patch.object(m, 'TTS_RATE', '-10%'), \
             patch.object(m, 'TTS_PITCH', '-2Hz'), \
             patch.object(m.edge_tts, 'Communicate', return_value=communicate) as clase, \
             patch.object(m.asyncio, 'create_subprocess_exec', AsyncMock(return_value=proc)):
            self.assertEqual(await m.synthesize_mulaw('Hola'), b'\xff' * 160)
        clase.assert_called_once_with(text='Hola', voice='gl-ES-RoiNeural',
                                      rate='-10%', pitch='-2Hz')


if __name__ == '__main__':
    unittest.main()
