"""Límite duro de caracteres por respuesta de Manolo (MAX_CHARS_RESPUESTA).

python3 -m unittest discover -s vps_backend/tests -v
"""
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from _stubs import manolo_ari as m


def respuesta_groq(contenido):
    choice = SimpleNamespace(message=SimpleNamespace(content=contenido), finish_reason='stop')
    return SimpleNamespace(choices=[choice])


class Recorte(unittest.TestCase):
    def test_respuesta_corta_sin_tocar(self):
        texto = '¿Iberdrola? Anda, mira tú. ¿Y eso qué es, hijo?'
        self.assertEqual(m.limitar_longitud(texto, 180), texto)

    def test_justo_en_el_limite_sin_tocar(self):
        texto = 'a' * 179 + '.'
        self.assertEqual(m.limitar_longitud(texto, 180), texto)

    def test_corta_en_el_ultimo_punto_antes_del_limite(self):
        texto = 'Pues mira, hijo. Mi Carmen dice que la luz es un robo. Y el butano, ni te cuento lo que vale'
        recortado = m.limitar_longitud(texto, 70)
        self.assertEqual(recortado, 'Pues mira, hijo. Mi Carmen dice que la luz es un robo.')
        self.assertLessEqual(len(recortado), 70)

    def test_corta_en_la_ultima_interrogacion(self):
        texto = '¿Fibra, dices? ¿Eso es lo de la tele nueva? Porque el vecino Paco tiene una y no sabe ni encenderla'
        recortado = m.limitar_longitud(texto, 60)
        self.assertEqual(recortado, '¿Fibra, dices? ¿Eso es lo de la tele nueva?')

    def test_corta_en_la_ultima_exclamacion(self):
        texto = '¡Anda, qué cosas! Pues yo de eso no sé nada, pero mi hijo que está en Alemania seguro que sí'
        self.assertEqual(m.limitar_longitud(texto, 50), '¡Anda, qué cosas!')

    def test_sin_puntuacion_corta_en_la_ultima_coma_y_pone_puntos_suspensivos(self):
        texto = 'Pues mira yo de eso no entiendo, pero mi Carmen que es muy lista seguro que lo sabe'
        recortado = m.limitar_longitud(texto, 50)
        self.assertEqual(recortado, 'Pues mira yo de eso no entiendo, pero mi Carmen…')
        self.assertLessEqual(len(recortado), 50)

    def test_sin_puntuacion_ni_comas_corta_en_el_ultimo_espacio(self):
        texto = 'Pues mira yo de eso no entiendo nada de nada porque soy de pueblo'
        recortado = m.limitar_longitud(texto, 30)
        self.assertEqual(recortado, 'Pues mira yo de eso no…')
        self.assertLessEqual(len(recortado), 30)

    def test_nunca_corta_una_palabra_por_la_mitad(self):
        texto = 'Pues mira yo de eso no entiendo nada de nada porque soy de pueblo'
        recortado = m.limitar_longitud(texto, 30).rstrip('…')
        self.assertTrue(texto.startswith(recortado + ' '))


class LecturaDelEnv(unittest.TestCase):
    def test_valores(self):
        with patch.object(m, 'ari_log'):
            self.assertEqual(m._leer_max_chars(None), 180)
            self.assertEqual(m._leer_max_chars('240'), 240)
            self.assertEqual(m._leer_max_chars('5'), 180)       # demasiado corto
            self.assertEqual(m._leer_max_chars('mucho'), 180)


class EnLaRespuestaDeGroq(unittest.IsolatedAsyncioTestCase):
    async def generar(self, contenido):
        logs = []
        with patch.object(m.groq_client.chat.completions, 'create',
                          AsyncMock(return_value=respuesta_groq(contenido))), \
             patch.object(m, 'MAX_CHARS_RESPUESTA', 60), \
             patch.object(m, 'ari_log', side_effect=logs.append):
            texto = await m.generate_manolo_reply([], 'Hola')
        return texto, logs

    async def test_larga_se_recorta_y_queda_en_el_log(self):
        larga = 'Ay, hijo, qué cosas me dices. Mi Carmen siempre dice que eso es para los jóvenes de ahora'
        texto, logs = await self.generar(larga)
        self.assertEqual(texto, 'Ay, hijo, qué cosas me dices.')
        self.assertIn(f'Respuesta recortada: {len(larga)} → {len(texto)} caracteres', logs)

    async def test_corta_no_deja_log(self):
        texto, logs = await self.generar('¿Eh? ¿Quién es?')
        self.assertEqual(texto, '¿Eh? ¿Quién es?')
        self.assertFalse(any('recortada' in l for l in logs))

    async def test_el_historial_guarda_lo_que_dijo_de_verdad(self):
        historial = []
        with patch.object(m.groq_client.chat.completions, 'create',
                          AsyncMock(return_value=respuesta_groq('Uno. ' + 'dos ' * 40))), \
             patch.object(m, 'MAX_CHARS_RESPUESTA', 60), \
             patch.object(m, 'ari_log'):
            await m.get_manolo_response(historial, 'Hola')
        self.assertEqual(historial, ['Hola', 'Uno.'])


if __name__ == '__main__':
    unittest.main()
