"""Cartesia como voz principal, Edge (Roi) de reserva y mensaje fijo al final.

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


def cliente_http(r, metodo='post'):
    cliente = MagicMock()
    setattr(cliente, metodo, AsyncMock(return_value=r))
    contexto = MagicMock()
    contexto.__aenter__ = AsyncMock(return_value=cliente)
    contexto.__aexit__ = AsyncMock(return_value=False)
    return contexto, getattr(cliente, metodo)


class ConEstadoTemporal(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.logs = []
        for p in (
            patch.object(m, 'CARTESIA_STATE_PATH', os.path.join(self.tmp.name, 'cartesia_state.json')),
            patch.object(m, 'CALL_LOG_PATH', os.path.join(self.tmp.name, 'call_log.json')),
            patch.object(m, 'CARTESIA_API_KEY', 'sk_car_test'),
            patch.object(m, 'CARTESIA_VOICE_ID', '35b2cfc1-e6fb-4d69-a598-c1780612be4a'),
            patch.object(m, 'ari_log', side_effect=self.logs.append),
        ):
            p.start()
            self.addCleanup(p.stop)


class Peticion(ConEstadoTemporal):
    async def test_pide_mulaw_8khz_en_crudo_con_la_voz_y_la_velocidad(self):
        contexto, post = cliente_http(respuesta(content=b'\x7f' * 160))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto), \
             patch.object(m, 'CARTESIA_SPEED', 0.9):
            audio = await m.synthesize_cartesia('Hola, hijo.')
        self.assertEqual(audio, b'\x7f' * 160)   # sin pasar por ffmpeg
        url = post.await_args.args[0]
        cuerpo = post.await_args.kwargs['json']
        cabeceras = post.await_args.kwargs['headers']
        self.assertEqual(url, 'https://api.cartesia.ai/tts/bytes')
        self.assertEqual(cabeceras['Authorization'], 'Bearer sk_car_test')
        self.assertEqual(cabeceras['Cartesia-Version'], '2026-08-14')
        self.assertEqual(cuerpo['output_format'],
                         {'container': 'raw', 'encoding': 'pcm_mulaw', 'sample_rate': 8000})
        self.assertEqual(cuerpo['voice'], {'id': '35b2cfc1-e6fb-4d69-a598-c1780612be4a'})
        self.assertEqual(cuerpo['generation_config'], {'speed': 0.9})
        self.assertEqual((cuerpo['transcript'], cuerpo['language']), ('Hola, hijo.', 'es'))

    async def test_sin_clave_es_error_de_tts(self):
        with patch.object(m, 'CARTESIA_API_KEY', None):
            with self.assertRaises(m.TTSError):
                await m.synthesize_cartesia('Hola')

    async def test_402_es_sin_creditos(self):
        contexto, _ = cliente_http(respuesta(402, b'', 'Payment Required'))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            with self.assertRaises(m.CartesiaSinCreditos):
                await m.synthesize_cartesia('Hola')

    def test_que_cuenta_como_sin_creditos(self):
        self.assertTrue(m.es_sin_creditos(402, ''))
        self.assertTrue(m.es_sin_creditos(403, '{"error": "Insufficient credits"}'))
        self.assertTrue(m.es_sin_creditos(429, 'monthly quota exceeded'))
        self.assertFalse(m.es_sin_creditos(429, 'rate limit: too many requests'))
        self.assertFalse(m.es_sin_creditos(401, 'invalid api key'))
        self.assertFalse(m.es_sin_creditos(500, 'credit service unavailable'))

    async def test_timeout_corto_de_cartesia(self):
        self.assertEqual(m.CARTESIA_TIMEOUT, 3)
        self.assertLess(m.CARTESIA_TIMEOUT, m.TTS_TIMEOUT)

        async def colgada(_texto):
            await asyncio.sleep(3600)

        with patch.object(m, 'CARTESIA_TIMEOUT', 0.05), \
             patch.object(m, '_synthesize_cartesia', colgada):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_cartesia('Hola')
        self.assertIn('timeout de 0.05s', str(ctx.exception))

    async def test_otros_errores_no_marcan_sin_creditos(self):
        contexto, _ = cliente_http(respuesta(500, b'', 'internal error'))
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto):
            with self.assertRaises(m.TTSError) as ctx:
                await m.synthesize_cartesia('Hola')
        self.assertNotIsInstance(ctx.exception, m.CartesiaSinCreditos)
        self.assertIn('500', str(ctx.exception))


class Velocidad(unittest.TestCase):
    def test_valores(self):
        with patch.object(m, 'ari_log'):
            self.assertEqual(m._leer_velocidad(None), 1.0)
            self.assertEqual(m._leer_velocidad(''), 1.0)
            self.assertEqual(m._leer_velocidad('0.9'), 0.9)
            self.assertEqual(m._leer_velocidad('0.6'), 0.6)
            self.assertEqual(m._leer_velocidad('1.5'), 1.5)
            self.assertEqual(m._leer_velocidad('2'), 1.0)        # fuera de rango
            self.assertEqual(m._leer_velocidad('lento'), 1.0)    # no es un número


class DiaRenovacion(unittest.TestCase):
    def test_por_defecto_el_dia_1(self):
        self.assertEqual(m.proxima_renovacion(date(2026, 10, 24), 1), date(2026, 11, 1))
        self.assertEqual(m.proxima_renovacion(date(2026, 12, 15), 1), date(2027, 1, 1))

    def test_si_el_dia_aun_no_ha_llegado_es_este_mes(self):
        # Agotado el 24 con renovación el 28: Cartesia vuelve el 28 de este mes
        self.assertEqual(m.proxima_renovacion(date(2026, 10, 24), 28), date(2026, 10, 28))

    def test_si_ya_paso_o_es_hoy_es_el_mes_siguiente(self):
        self.assertEqual(m.proxima_renovacion(date(2026, 10, 24), 15), date(2026, 11, 15))
        self.assertEqual(m.proxima_renovacion(date(2026, 10, 15), 15), date(2026, 11, 15))

    def test_un_dia_que_el_mes_no_tiene_es_su_ultimo_dia(self):
        self.assertEqual(m.proxima_renovacion(date(2026, 11, 5), 31), date(2026, 11, 30))
        self.assertEqual(m.proxima_renovacion(date(2027, 1, 31), 31), date(2027, 2, 28))

    def test_lectura_del_env(self):
        with patch.object(m, 'ari_log'):
            self.assertEqual(m._leer_dia_renovacion(None), 1)
            self.assertEqual(m._leer_dia_renovacion('15'), 15)
            self.assertEqual(m._leer_dia_renovacion('0'), 1)
            self.assertEqual(m._leer_dia_renovacion('32'), 1)
            self.assertEqual(m._leer_dia_renovacion('[el día]'), 1)


class CreditosAgotados(ConEstadoTemporal):
    def test_marca_hasta_el_dia_de_renovacion(self):
        with patch.object(m, 'CARTESIA_DIA_RENOVACION', 15):
            self.assertEqual(m.marcar_cartesia_agotado(date(2026, 10, 24)), date(2026, 11, 15))

    def test_marca_hasta_el_dia_1_y_luego_caduca(self):
        self.assertIsNone(m.cartesia_agotado_hasta(date(2026, 10, 24)))
        with patch.object(m, 'CARTESIA_DIA_RENOVACION', 1):
            m.marcar_cartesia_agotado(date(2026, 10, 24))
        self.assertEqual(m.cartesia_agotado_hasta(date(2026, 10, 31)), date(2026, 11, 1))
        self.assertIsNone(m.cartesia_agotado_hasta(date(2026, 11, 1)))

    def test_fichero_corrupto_no_bloquea_cartesia(self):
        with open(m.CARTESIA_STATE_PATH, 'w') as f:
            f.write('{no es json')
        self.assertIsNone(m.cartesia_agotado_hasta())


class Escalera(ConEstadoTemporal):
    def setUp(self):
        super().setUp()
        self.saltos = []
        self.edge = AsyncMock(return_value=b'edge')
        p = patch.object(m, 'synthesize_edge', self.edge)
        p.start()
        self.addCleanup(p.stop)

    async def sintetizar(self, cartesia, proveedor='cartesia'):
        with patch.object(m, 'TTS_PROVIDER', proveedor), \
             patch.object(m, 'synthesize_cartesia', cartesia):
            return await m.synthesize_mulaw('Hola', al_saltar=lambda p, e: self.saltos.append(p))

    async def test_cartesia_bien_no_toca_edge(self):
        self.assertEqual(await self.sintetizar(AsyncMock(return_value=b'cartesia')), b'cartesia')
        self.edge.assert_not_called()
        self.assertEqual(self.saltos, [])

    async def test_cartesia_falla_y_sigue_con_edge(self):
        audio = await self.sintetizar(AsyncMock(side_effect=m.TTSError('Cartesia 500')))
        self.assertEqual(audio, b'edge')
        self.assertEqual(self.saltos, ['Cartesia'])
        self.assertIsNone(m.cartesia_agotado_hasta())   # un 500 no son créditos

    async def test_sin_creditos_marca_el_estado_y_sigue_con_edge(self):
        audio = await self.sintetizar(AsyncMock(side_effect=m.CartesiaSinCreditos('Cartesia 402')))
        self.assertEqual(audio, b'edge')
        self.assertEqual(self.saltos, ['Cartesia'])
        self.assertIsNotNone(m.cartesia_agotado_hasta())

    async def test_con_creditos_agotados_ni_lo_intenta(self):
        m.marcar_cartesia_agotado()
        cartesia = AsyncMock()
        self.assertEqual(await self.sintetizar(cartesia), b'edge')
        cartesia.assert_not_called()
        self.assertEqual(self.saltos, ['Cartesia'])

    async def test_con_proveedor_edge_no_usa_cartesia(self):
        cartesia = AsyncMock()
        self.assertEqual(await self.sintetizar(cartesia, proveedor='edge'), b'edge')
        cartesia.assert_not_called()
        self.assertEqual(self.saltos, [])

    async def test_latencia_de_cada_sintesis_con_su_proveedor(self):
        await self.sintetizar(AsyncMock(return_value=b'\xff' * 160))
        self.edge.side_effect = None
        await self.sintetizar(AsyncMock(), proveedor='edge')
        latencias = [l for l in self.logs if l.startswith('TTS ')]
        self.assertRegex(latencias[0], r'^TTS Cartesia: \d+ ms \(160 bytes\)$')
        self.assertRegex(latencias[1], r'^TTS Edge: \d+ ms \(4 bytes\)$')

    async def test_latencia_tambien_cuando_falla(self):
        await self.sintetizar(AsyncMock(side_effect=m.TTSError('Cartesia 500')))
        latencias = [l for l in self.logs if l.startswith('TTS ')]
        self.assertRegex(latencias[0], r'^TTS Cartesia: falló tras \d+ ms$')
        self.assertRegex(latencias[1], r'^TTS Edge: \d+ ms')

    async def test_sin_probar_cartesia_va_directo_a_edge(self):
        cartesia = AsyncMock()
        with patch.object(m, 'TTS_PROVIDER', 'cartesia'), \
             patch.object(m, 'synthesize_cartesia', cartesia):
            audio = await m.synthesize_mulaw('Hola', probar_cartesia=False)
        self.assertEqual(audio, b'edge')
        cartesia.assert_not_called()

    async def test_si_tambien_falla_edge_sube_el_error(self):
        self.edge.side_effect = m.TTSError('Edge TTS: 403')
        with self.assertRaises(m.TTSError):
            await self.sintetizar(AsyncMock(side_effect=m.TTSError('Cartesia 500')))
        self.assertEqual(self.saltos, ['Cartesia'])


class EnUnaLlamada(ConEstadoTemporal):
    def setUp(self):
        super().setUp()
        self.run_fixed = AsyncMock()
        p = patch.object(m, 'run_fixed', self.run_fixed)
        p.start()
        self.addCleanup(p.stop)
        self.state = m.CallState('canal-1')
        self.state.transport = MagicMock()
        self.state.remote_addr = ('127.0.0.1', 4000)
        with open(m.CALL_LOG_PATH, 'w') as f:
            json.dump([{'id': 'canal-1', 'modo': 'AI', 'plan_b': None}], f)

    def plan_b(self):
        with open(m.CALL_LOG_PATH) as f:
            return json.load(f)[0]['plan_b']

    def fallbacks(self):
        return [l for l in self.logs if l.startswith('FALLBACK:')]

    async def hablar(self, veces, edge):
        self.cartesia = AsyncMock(side_effect=m.TTSError('Cartesia 500'))
        with patch.object(m, 'TTS_PROVIDER', 'cartesia'), \
             patch.object(m, 'synthesize_cartesia', self.cartesia), \
             patch.object(m, 'synthesize_edge', edge), \
             patch.object(m.asyncio, 'sleep', AsyncMock()):
            for _ in range(veces):
                await m.play_response(self.state, 'Hola')
        # Fuera del parche: el sleep simulado no cede el bucle y run_fixed no correría
        await asyncio.sleep(0)

    async def test_si_cartesia_falla_el_resto_de_la_llamada_va_directo_a_edge(self):
        await self.hablar(3, AsyncMock(return_value=b'\xff' * 160))
        self.cartesia.assert_awaited_once()          # no se reintenta en cada respuesta
        latencias = [l for l in self.logs if l.startswith('TTS ')]
        self.assertEqual([l.split(':')[0] for l in latencias],
                         ['TTS Cartesia', 'TTS Edge', 'TTS Edge', 'TTS Edge'])
        self.assertEqual(sum('falló tras' in l for l in latencias), 1)
        self.assertEqual(len(self.fallbacks()), 1)
        self.assertIn('FALLBACK: Cartesia', self.fallbacks()[0])
        self.assertEqual(self.plan_b(), 'Cartesia')
        self.run_fixed.assert_not_called()
        self.assertEqual(self.state.transport.sendto.call_count, 3)   # siguió hablando

    async def test_cartesia_y_edge_caidos_mensaje_fijo(self):
        await self.hablar(1, AsyncMock(side_effect=m.TTSError('Edge TTS: 403')))
        self.assertEqual(self.plan_b(), 'Cartesia → TTS')
        self.assertEqual([l.split(' falló')[0] for l in self.fallbacks()],
                         ['FALLBACK: Cartesia', 'FALLBACK: TTS'])
        self.run_fixed.assert_awaited_once_with('canal-1')


class HealthcheckCartesia(ConEstadoTemporal):
    def setUp(self):
        super().setUp()
        for p in (patch.object(hc, 'log'), patch.object(m, 'TTS_PROVIDER', 'cartesia')):
            p.start()
            self.addCleanup(p.stop)

    def test_solo_con_cartesia_hay_comprobaciones_de_cartesia(self):
        with patch.object(m, 'TTS_PROVIDER', 'edge'):
            self.assertNotIn('Cartesia', hc.construir_checks())
        self.assertIn('Cartesia', hc.construir_checks())
        self.assertIn('Cartesia: sin créditos', hc.construir_checks())

    def test_el_saldo_solo_con_clave_admin_y_creditos_del_plan(self):
        with patch.object(hc, 'CARTESIA_ADMIN_API_KEY', None):
            self.assertNotIn('Cartesia: saldo bajo', hc.construir_checks())
        with patch.object(hc, 'CARTESIA_ADMIN_API_KEY', 'sk_car_admin_x'), \
             patch.object(hc, 'CARTESIA_CREDITOS_MES', '100000'):
            self.assertIn('Cartesia: saldo bajo', hc.construir_checks())

    async def test_cartesia_se_comprueba_sin_sintetizar(self):
        contexto, get = cliente_http(respuesta(datos={'id': 'v'}), 'get')
        sintetizar = AsyncMock()
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto), \
             patch.object(m, 'synthesize_cartesia', sintetizar):
            resultado = await hc.run_checks({'Cartesia': hc.check_cartesia})
        self.assertEqual(resultado, {'Cartesia': None})
        self.assertTrue(get.await_args.args[0].endswith('/voices/35b2cfc1-e6fb-4d69-a598-c1780612be4a'))
        sintetizar.assert_not_called()

    async def saldo(self, usados, total='10000'):
        contexto, get = cliente_http(respuesta(datos={'data': [{'credits': usados}]}), 'get')
        estado = {}
        with patch.object(m.httpx, 'AsyncClient', return_value=contexto), \
             patch.object(hc, 'CARTESIA_ADMIN_API_KEY', 'sk_car_admin_x'), \
             patch.object(hc, 'CARTESIA_CREDITOS_MES', total):
            resultado = await hc.run_checks({
                'Cartesia: saldo bajo': hc.check_cartesia_saldo(estado),
                'Cartesia: sin créditos': hc.check_cartesia_creditos(estado),
            })
        self.assertEqual(get.await_args.kwargs['headers']['Authorization'], 'Bearer sk_car_admin_x')
        return resultado

    async def test_saldo_suficiente(self):
        self.assertEqual(await self.saldo(5000),
                         {'Cartesia: saldo bajo': None, 'Cartesia: sin créditos': None})

    async def test_menos_del_20_por_ciento_avisa(self):
        resultado = await self.saldo(8500)
        self.assertIn('quedan 15%', resultado['Cartesia: saldo bajo'])
        self.assertIsNone(resultado['Cartesia: sin créditos'])

    async def test_agotado_por_consumo_avisa_de_las_dos_cosas(self):
        resultado = await self.saldo(10200)
        self.assertIn('quedan 0%', resultado['Cartesia: saldo bajo'])
        self.assertIn('sin créditos', resultado['Cartesia: sin créditos'])

    async def test_agotado_por_el_estado_que_dejo_una_llamada(self):
        m.marcar_cartesia_agotado()
        resultado = await hc.run_checks({'Cartesia: sin créditos': hc.check_cartesia_creditos({})})
        self.assertIn('hasta el', resultado['Cartesia: sin créditos'])

    def test_saldo_bajo_y_agotado_son_dos_emails_distintos(self):
        bajo = {'Cartesia: saldo bajo': 'quedan 15%'}
        agotado = {**bajo, 'Cartesia: sin créditos': 'sin créditos'}
        self.assertEqual(len(hc.build_emails({}, bajo)), 1)
        self.assertEqual(len(hc.build_emails(bajo, agotado)), 1)   # el segundo aviso
        self.assertEqual(hc.build_emails(agotado, agotado), [])    # sin repetirse


if __name__ == '__main__':
    unittest.main()
