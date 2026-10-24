"""Plan B del Modo 3: cada pieza (TTS, Groq, Deepgram) falla por separado.

python3 -m unittest discover -s vps_backend/tests -v
"""
import asyncio
import os
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from _stubs import manolo_ari as m


def nueva_llamada():
    state = m.CallState('canal-1')
    state.transport = MagicMock()
    state.remote_addr = ('127.0.0.1', 4000)
    state.pcm_buffer = bytearray(b'\x00\x01' * 800)
    return state


class Base(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.logs = []
        self.run_fixed = AsyncMock()
        self.hangup = AsyncMock()
        # activar_plan_b escribe en el call log: nunca en el real del VPS
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        for p in (
            patch.object(m, 'CALL_LOG_PATH', os.path.join(self.tmp.name, 'call_log.json')),
            patch.object(m, 'ari_log', side_effect=self.logs.append),
            patch.object(m, 'run_fixed', self.run_fixed),
            patch.object(m.ari, 'hangup', self.hangup),
        ):
            p.start()
            self.addCleanup(p.stop)

    def fallbacks(self):
        return [l for l in self.logs if l.startswith('FALLBACK:')]

    async def esperar_tareas(self):
        actual = asyncio.current_task()
        pendientes = [t for t in asyncio.all_tasks() if t is not actual]
        if pendientes:
            await asyncio.gather(*pendientes, return_exceptions=True)


class FlujoNormal(Base):
    async def test_todo_funciona_no_hay_plan_b(self):
        state = nueva_llamada()
        with patch.object(m, 'deepgram_stt', AsyncMock(return_value='Hola, le llamo de Iberdrola')), \
             patch.object(m, 'generate_manolo_reply', AsyncMock(return_value='¿Iberdrola? Anda.')), \
             patch.object(m, 'synthesize_mulaw', AsyncMock(return_value=b'\xff' * 320)):
            await m.handle_turn_complete(state)
            await self.esperar_tareas()

        self.assertEqual(state.chat_history, ['Hola, le llamo de Iberdrola', '¿Iberdrola? Anda.'])
        self.assertEqual(state.transport.sendto.call_count, 2)  # 320 bytes = 2 frames RTP
        self.assertFalse(state.plan_b)
        self.run_fixed.assert_not_called()
        self.assertEqual(self.fallbacks(), [])

    async def test_silencio_no_cuenta_como_error_de_deepgram(self):
        state = nueva_llamada()
        with patch.object(m, 'deepgram_stt', AsyncMock(return_value='')), \
             patch.object(m, 'despedida_y_cierre', AsyncMock()) as despedida:
            for _ in range(m.MAX_SILENCIO):
                state.pcm_buffer = bytearray(b'\x00\x01' * 800)
                await m.handle_turn_complete(state)

        despedida.assert_awaited_once()  # el comportamiento de siempre
        self.assertEqual(state.errores_stt_consecutivos, 0)
        self.run_fixed.assert_not_called()


class FalloTTS(Base):
    async def test_edge_tts_403_reproduce_mensaje_fijo(self):
        state = nueva_llamada()
        communicate = MagicMock()
        communicate.save = AsyncMock(side_effect=Exception('403, message=Invalid response status'))
        with patch.object(m.edge_tts, 'Communicate', return_value=communicate):
            await m.play_response(state, 'Hola')
            await self.esperar_tareas()

        self.assertTrue(state.plan_b)
        self.run_fixed.assert_awaited_once_with('canal-1')
        self.assertEqual(len(self.fallbacks()), 1)
        self.assertIn('TTS', self.fallbacks()[0])
        self.assertIn('403', self.fallbacks()[0])
        state.transport.sendto.assert_not_called()

    async def test_ffmpeg_con_error_es_fallo_de_tts(self):
        communicate = MagicMock()
        communicate.save = AsyncMock()
        proc = MagicMock(returncode=1)
        proc.communicate = AsyncMock(return_value=(b'', b'Invalid data found when processing input\n'))
        with patch.object(m.edge_tts, 'Communicate', return_value=communicate), \
             patch.object(m.asyncio, 'create_subprocess_exec', AsyncMock(return_value=proc)):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_mulaw('Hola')
        self.assertIn('ffmpeg', str(ctx.exception))
        self.assertIn('Invalid data', str(ctx.exception))

    async def test_tts_colgado_salta_por_timeout(self):
        async def nunca_termina(_path):
            await asyncio.sleep(3600)

        communicate = MagicMock()
        communicate.save = nunca_termina
        with patch.object(m, 'TTS_TIMEOUT', 0.05), \
             patch.object(m.edge_tts, 'Communicate', return_value=communicate):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_mulaw('Hola')
        self.assertIn('timeout', str(ctx.exception))

    async def test_fallo_tts_en_la_despedida_no_corta_el_mensaje_fijo(self):
        state = nueva_llamada()
        sleep_real = asyncio.sleep

        async def sleep_rapido(_segundos):
            # Cede el bucle como el sleep(4) real, sin esperar 4 s
            for _ in range(5):
                await sleep_real(0)

        with patch.object(m, 'synthesize_mulaw', AsyncMock(side_effect=m.TTSError('Edge TTS: 403'))), \
             patch.object(m.asyncio, 'sleep', sleep_rapido):
            await m.despedida_y_cierre(state)
            await self.esperar_tareas()

        self.run_fixed.assert_awaited_once_with('canal-1')
        self.hangup.assert_not_called()  # cuelga run_fixed, no la despedida

    async def test_plan_b_solo_se_activa_una_vez(self):
        state = nueva_llamada()
        m.activar_plan_b(state, 'TTS', 'x')
        m.activar_plan_b(state, 'Deepgram', 'y')
        await self.esperar_tareas()
        self.run_fixed.assert_awaited_once()
        self.assertEqual(len(self.fallbacks()), 1)


class FalloGroq(Base):
    async def test_groq_caido_usa_frase_de_emergencia(self):
        state = nueva_llamada()
        sintetizado = AsyncMock(return_value=b'\xff' * 160)
        groq = AsyncMock(side_effect=Exception('Connection error'))
        with patch.object(m, 'deepgram_stt', AsyncMock(return_value='Hola')), \
             patch.object(m.groq_client.chat.completions, 'create', groq), \
             patch.object(m, 'synthesize_mulaw', sintetizado):
            await m.handle_turn_complete(state)
            await self.esperar_tareas()

        sintetizado.assert_awaited_once()
        self.assertEqual(sintetizado.await_args.args[0], m.FRASE_EMERGENCIA)
        self.assertEqual(state.chat_history, [])
        self.run_fixed.assert_not_called()
        self.assertEqual(len(self.fallbacks()), 1)
        self.assertIn('Groq', self.fallbacks()[0])

    async def test_groq_vacio_cuenta_como_fallo(self):
        respuesta = MagicMock()
        respuesta.choices = [MagicMock()]
        respuesta.choices[0].message.content = '   '
        with patch.object(m.groq_client.chat.completions, 'create', AsyncMock(return_value=respuesta)):
            self.assertEqual(await m.get_manolo_response([], 'Hola'), m.FRASE_EMERGENCIA)

    async def test_groq_y_tts_caidos_mensaje_fijo(self):
        state = nueva_llamada()
        with patch.object(m, 'deepgram_stt', AsyncMock(return_value='Hola')), \
             patch.object(m, 'generate_manolo_reply', AsyncMock(side_effect=m.LLMError('500'))), \
             patch.object(m, 'synthesize_mulaw', AsyncMock(side_effect=m.TTSError('Edge TTS: 403'))):
            await m.handle_turn_complete(state)
            await self.esperar_tareas()

        self.run_fixed.assert_awaited_once_with('canal-1')
        piezas = [l.split(' falló')[0] for l in self.fallbacks()]
        self.assertEqual(piezas, ['FALLBACK: Groq', 'FALLBACK: TTS'])


class FalloDeepgram(Base):
    async def turno(self, state):
        state.pcm_buffer = bytearray(b'\x00\x01' * 800)
        await m.handle_turn_complete(state)
        await self.esperar_tareas()

    async def test_error_http_se_convierte_en_stt_error(self):
        cliente = MagicMock()
        cliente.post = AsyncMock(side_effect=Exception('401 Unauthorized'))
        contexto = MagicMock()
        contexto.__aenter__ = AsyncMock(return_value=cliente)
        contexto.__aexit__ = AsyncMock(return_value=False)
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            with self.assertRaises(m.STTError):
                await m.deepgram_stt(b'\x00\x01' * 800)

    async def test_un_error_no_basta(self):
        state = nueva_llamada()
        with patch.object(m, 'deepgram_stt', AsyncMock(side_effect=m.STTError('timeout'))):
            await self.turno(state)
        self.assertFalse(state.plan_b)
        self.assertEqual(state.silencio_consecutivo, 0)
        self.run_fixed.assert_not_called()

    async def test_dos_errores_seguidos_mensaje_fijo(self):
        state = nueva_llamada()
        with patch.object(m, 'deepgram_stt', AsyncMock(side_effect=m.STTError('503'))):
            await self.turno(state)
            await self.turno(state)
        self.run_fixed.assert_awaited_once_with('canal-1')
        self.assertEqual(len(self.fallbacks()), 1)
        self.assertIn('Deepgram', self.fallbacks()[0])

    async def test_una_respuesta_buena_reinicia_la_cuenta(self):
        state = nueva_llamada()
        respuestas = [m.STTError('503'), '', m.STTError('503')]
        with patch.object(m, 'deepgram_stt', AsyncMock(side_effect=respuestas)):
            for _ in respuestas:
                await self.turno(state)
        self.assertFalse(state.plan_b)
        self.run_fixed.assert_not_called()


if __name__ == '__main__':
    unittest.main()
