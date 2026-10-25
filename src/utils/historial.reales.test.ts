import { describe, it, expect, beforeAll } from 'vitest';
import { fusionarHistorial, fusionarHistorialSeguro, parseServidor, etiquetaServidor } from './historial';
import type { ServerCallRecord } from '../services/BackendSyncService';

// Los tres últimos registros de call_log.json del VPS del 25/10/2026, tal cual
const REALES = [
  { id: '1792819885.14', numero: '+34622696266', timestamp_inicio: '2026-10-24T05:31:27.027470+00:00', timestamp_fin: '2026-10-24T05:31:50.052688+00:00', duracion_segundos: 23, estado: 'finalizada', modo: 'AI', plan_b: null },
  { id: '1792941411.17', numero: '+34622696266', timestamp_inicio: '2026-10-25T15:16:53.233625+00:00', timestamp_fin: '2026-10-25T15:17:53.857997+00:00', duracion_segundos: 60, estado: 'finalizada', modo: 'AI', plan_b: null, personaje: 'manolo' },
  { id: '1792941482.20', numero: '+34622696266', timestamp_inicio: '2026-10-25T15:18:04.339570+00:00', timestamp_fin: '2026-10-25T15:19:32.968410+00:00', duracion_segundos: 88, estado: 'finalizada', modo: 'AI', plan_b: null, personaje: 'paquita' },
] as ServerCallRecord[];

beforeAll(() => {
  process.env.TZ = 'Europe/Madrid';
});

describe('registros reales del 25/10/2026', () => {
  it('las fechas con microsegundos se parsean', () => {
    for (const r of REALES) {
      expect(Number.isNaN(parseServidor(r.timestamp_inicio).getTime())).toBe(false);
    }
  });

  it('las etiquetas por personaje', () => {
    expect(REALES.map(etiquetaServidor)).toEqual([
      'Manolo (Modo 3)', 'Manolo (Modo 3)', 'Paquita (Modo 3)',
    ]);
  });

  it('fusionarHistorial no lanza y devuelve las tres, la más reciente primero', () => {
    const filas = fusionarHistorial([], REALES);
    expect(filas.map(f => f.reason)).toEqual([
      'Paquita (Modo 3)', 'Manolo (Modo 3)', 'Manolo (Modo 3)',
    ]);
    expect(filas[0].timestamp).toBe('2026-10-25 16:18:04');
  });
});

describe('un registro raro no vacía el historial', () => {
  it('los microsegundos se recortan a milisegundos (Hermes solo garantiza 3 cifras)', () => {
    const d = parseServidor('2026-10-25T15:16:53.233625+00:00');
    expect(d.toISOString()).toBe('2026-10-25T15:16:53.233Z');
  });

  it('un registro roto del servidor se salta y el resto se ve', () => {
    const rotos = [null, { id: 'sin-fecha', numero: '600', estado: 'finalizada' }, 42];
    const { filas, errores } = fusionarHistorialSeguro([], [...REALES, ...rotos]);
    expect(filas).toHaveLength(3);
    expect(errores).toHaveLength(3);
    expect(errores[1]).toContain('Servidor, registro sin-fecha');
  });

  it('un registro local roto se salta y el del servidor se ve', () => {
    const locales = [
      { number: '900111222', timestamp: '2026-10-25 10:00:00', reason: 'Premium', action: 'Answer+Hangup' },
      { number: '900333444', reason: 'Premium', action: 'Answer+Hangup' },
    ];
    const { filas, errores } = fusionarHistorialSeguro(locales, REALES);
    expect(filas.map(f => f.origen)).toEqual(['servidor', 'servidor', 'local', 'servidor']);
    expect(errores).toEqual([expect.stringContaining('Local, registro #2')]);
  });

  it('si el servidor no devuelve una lista, lo local se ve igual', () => {
    const locales = [{ number: '900111222', timestamp: '2026-10-25 10:00:00', reason: 'Premium', action: 'Answer+Hangup' }];
    const { filas, errores } = fusionarHistorialSeguro(locales, { error: 'algo' });
    expect(filas).toHaveLength(1);
    expect(errores).toEqual(['Servidor: no es una lista (object)']);
  });

  it('fusionarHistorial sigue devolviendo solo las filas', () => {
    expect(fusionarHistorial([], REALES)).toHaveLength(3);
  });
});
