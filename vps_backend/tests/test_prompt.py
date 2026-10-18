"""PROMPT_MANOLO: sin frases hechas que el modelo copie, pero con las muletillas.

python3 -m unittest discover -s vps_backend/tests -v
"""
import unittest

from _stubs import manolo_ari as m


class PromptManolo(unittest.TestCase):
    def test_sin_las_frases_literales_que_repetia(self):
        # "fibra de los chinos" salió 3 veces en una llamada real (18/10/2026)
        for frase in ('esa cosa de los', 'lo del entierro', 'es "la lotería"'):
            self.assertNotIn(frase, m.PROMPT_MANOLO)

    def test_mantiene_los_ejemplos_de_muletillas(self):
        # Dan el acento gallego; quitarlos no arreglaba nada
        for muletilla in ('"anda"', '"mira tú"', '"fíjate"', '"que si"'):
            self.assertIn(muletilla, m.PROMPT_MANOLO)

    def test_regla_de_uso_unico_con_excepcion_para_muletillas(self):
        self.assertIn('REGLA ESTRICTA', m.PROMPT_MANOLO)
        self.assertIn('no uses la misma en dos respuestas seguidas', m.PROMPT_MANOLO)


if __name__ == '__main__':
    unittest.main()
