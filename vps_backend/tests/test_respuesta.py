"""Longitud de las respuestas de Manolo: prompt de 1-2 frases y sin cortes.

python3 -m unittest discover -s vps_backend/tests -v
"""
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from _stubs import manolo_ari as m


def respuesta_groq(contenido, finish_reason='stop'):
    choice = SimpleNamespace(
        message=SimpleNamespace(content=contenido), finish_reason=finish_reason)
    return SimpleNamespace(choices=[choice])


class Prompt(unittest.TestCase):
    def test_pide_una_o_dos_frases(self):
        self.assertIn('1-2 frases', m.PROMPT_MANOLO)
        self.assertNotIn('2-3 frases', m.PROMPT_MANOLO)


class RecortarUltimaFrase(unittest.TestCase):
    def test_completa_no_se_toca(self):
        self.assertEqual(m.recortar_ultima_frase('Hola, hijo. ¿Qué tal?'), 'Hola, hijo. ¿Qué tal?')

    def test_recorta_en_el_ultimo_signo(self):
        self.assertEqual(
            m.recortar_ultima_frase('Anda, Iberdrola. Pues mi Carmen dice que la luz es un ro'),
            'Anda, Iberdrola.')

    def test_respeta_interrogacion_y_exclamacion(self):
        self.assertEqual(m.recortar_ultima_frase('¡Qué me dices! Y el gato Mano'), '¡Qué me dices!')
        self.assertEqual(m.recortar_ultima_frase('¿Cómo? Que no te oi'), '¿Cómo?')

    def test_sin_signos_se_devuelve_igual(self):
        self.assertEqual(m.recortar_ultima_frase('Pues mira que yo'), 'Pues mira que yo')

    def test_vacio(self):
        self.assertEqual(m.recortar_ultima_frase('  '), '')


class GenerarRespuesta(unittest.IsolatedAsyncioTestCase):
    async def generar(self, resp):
        create = AsyncMock(return_value=resp)
        with patch.object(m.groq_client.chat.completions, 'create', create):
            texto = await m.generate_manolo_reply([], 'Hola')
        return texto, create

    async def test_margen_de_tokens(self):
        _, create = await self.generar(respuesta_groq('Anda.'))
        self.assertGreater(create.call_args.kwargs['max_tokens'], 100)

    async def test_cortada_por_longitud_se_recorta(self):
        texto, _ = await self.generar(
            respuesta_groq('Pues mira. Mi hijo en Alemania no llam', 'length'))
        self.assertEqual(texto, 'Pues mira.')

    async def test_terminada_normal_no_se_recorta(self):
        texto, _ = await self.generar(respuesta_groq('Pues mira que yo', 'stop'))
        self.assertEqual(texto, 'Pues mira que yo')

    async def test_vacia_sigue_lanzando_llm_error(self):
        with self.assertRaises(m.LLMError):
            await self.generar(respuesta_groq('', 'stop'))


if __name__ == '__main__':
    unittest.main()
