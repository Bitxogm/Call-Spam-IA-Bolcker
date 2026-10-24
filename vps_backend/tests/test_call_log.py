"""call_log.json: cada llamada guarda el modo que la atendió y si cayó al plan B.

python3 -m unittest discover -s vps_backend/tests -v
"""
import asyncio
import json
import os
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m


def evento_llamada(channel_id='canal-1', numero='+34400123456'):
    return {'channel': {'id': channel_id, 'name': 'PJSIP/zadarma-0001',
                        'caller': {'number': numero}}}


class CallLog(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.ruta = os.path.join(self.tmp.name, 'call_log.json')
        self.run_fixed = AsyncMock()
        for p in (
            patch.object(m, 'CALL_LOG_PATH', self.ruta),
            patch.object(m, 'ari_log'),
            patch.object(m, 'run_fixed', self.run_fixed),
            patch.object(m, 'play_response', AsyncMock()),
            patch.object(m.ari, 'answer', AsyncMock()),
            patch.object(m.ari, 'create_bridge', AsyncMock(return_value='bridge-1')),
            patch.object(m.ari, 'add_channel_to_bridge', AsyncMock()),
            patch.object(m.ari, 'create_external_media', AsyncMock(return_value='em-1')),
        ):
            p.start()
            self.addCleanup(p.stop)
        self.addCleanup(setattr, m, 'call_state', None)

    def registros(self):
        with open(self.ruta) as f:
            return json.load(f)

    async def entrar(self, modo):
        lecturas = MagicMock(return_value=modo)
        with patch.object(m, 'get_current_mode', lecturas):
            await m.on_stasis_start(evento_llamada())
            await asyncio.sleep(0)
        return lecturas

    async def test_modo_2_queda_como_fixed(self):
        await self.entrar('FIXED')
        registro = self.registros()[-1]
        self.assertEqual((registro['modo'], registro['plan_b']), ('FIXED', None))
        self.run_fixed.assert_awaited_once_with('canal-1')

    async def test_modo_3_queda_como_ai(self):
        await self.entrar('AI')
        registro = self.registros()[-1]
        self.assertEqual((registro['modo'], registro['plan_b']), ('AI', None))
        self.run_fixed.assert_not_called()

    async def test_el_modo_se_lee_una_vez_y_es_el_que_decide(self):
        # Si el fichero cambiara entre dos lecturas, el log y el camino podrían
        # no coincidir: por eso se lee una sola vez, antes de escribir el registro
        lecturas = await self.entrar('FIXED')
        lecturas.assert_called_once()

    async def test_plan_b_apunta_la_pieza_que_fallo(self):
        await self.entrar('AI')
        m.activar_plan_b(m.call_state, 'TTS', 'Edge TTS: 403')
        await asyncio.sleep(0)
        registro = self.registros()[-1]
        self.assertEqual((registro['modo'], registro['plan_b']), ('AI', 'TTS'))

    async def test_plan_b_solo_toca_su_llamada(self):
        with open(self.ruta, 'w') as f:
            json.dump([{'id': 'otra', 'modo': 'AI', 'plan_b': None}], f)
        await self.entrar('AI')
        m.activar_plan_b(m.call_state, 'Deepgram', '503')
        await asyncio.sleep(0)
        otra, esta = self.registros()
        self.assertIsNone(otra['plan_b'])
        self.assertEqual(esta['plan_b'], 'Deepgram')


if __name__ == '__main__':
    unittest.main()
