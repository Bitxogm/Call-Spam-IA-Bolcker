import { describe, it, expect, beforeAll } from 'vitest';
import { parseServidor, formatearLocal, fusionarHistorial } from './historial';
import type { SpamCallRecord } from '../services/CallHistoryService';
import type { ServerCallRecord } from '../services/BackendSyncService';

// Zona fija: el desfase UTC→local es justo lo que se está probando, así que el
// test no puede depender de la zona de la máquina que lo corre.
beforeAll(() => {
  process.env.TZ = 'Europe/Madrid';
});

const servidorRec = (over: Partial<ServerCallRecord> = {}): ServerCallRecord => ({
  id: 'PJSIP/zadarma-0001',
  numero: '900123456',
  timestamp_inicio: '2026-10-12T06:33:00+00:00',
  timestamp_fin: '2026-10-12T06:33:42+00:00',
  duracion_segundos: 42,
  estado: 'finalizada',
  ...over,
});

const localRec = (over: Partial<SpamCallRecord> = {}): SpamCallRecord => ({
  number: '902222333',
  timestamp: '2026-10-12 09:00:00',
  reason: 'Blacklist',
  action: 'Answer+Hangup',
  ...over,
});

describe('parseServidor', () => {
  it('acepta el offset explícito que escribe manolo_ari.py', () => {
    expect(parseServidor('2026-10-12T06:33:00+00:00').getTime()).toBe(
      Date.UTC(2026, 9, 12, 6, 33, 0)
    );
  });

  it('acepta la forma con Z', () => {
    expect(parseServidor('2026-10-12T06:33:00Z').getTime()).toBe(
      Date.UTC(2026, 9, 12, 6, 33, 0)
    );
  });

  it('trata como UTC un timestamp sin offset (registros previos al arreglo)', () => {
    // Eran UTC sin decirlo. Sin añadir la Z, JS los leería como hora local y
    // saldrían 2 h desplazados — era el bug.
    expect(parseServidor('2026-10-12T06:33:00').getTime()).toBe(
      Date.UTC(2026, 9, 12, 6, 33, 0)
    );
  });

  it('las tres formas caen en el mismo instante', () => {
    const [a, b, c] = [
      '2026-10-12T06:33:00+00:00',
      '2026-10-12T06:33:00Z',
      '2026-10-12T06:33:00',
    ].map(s => parseServidor(s).getTime());
    expect(a).toBe(b);
    expect(b).toBe(c);
  });
});

describe('formatearLocal', () => {
  it('usa el mismo formato que CallHistoryHelper.java', () => {
    expect(formatearLocal(new Date(Date.UTC(2026, 9, 12, 6, 33, 0)))).toMatch(
      /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/
    );
  });

  it('rellena con cero los componentes de un dígito', () => {
    // 3 de febrero, 04:05:06 local
    expect(formatearLocal(new Date(2026, 1, 3, 4, 5, 6))).toBe('2026-02-03 04:05:06');
  });

  it('convierte a hora local: 06:33 UTC son las 08:33 en Madrid (CEST)', () => {
    expect(formatearLocal(parseServidor('2026-10-12T06:33:00+00:00'))).toBe(
      '2026-10-12 08:33:00'
    );
  });
});

describe('fusionarHistorial', () => {
  it('con las dos fuentes vacías devuelve lista vacía', () => {
    expect(fusionarHistorial([], [])).toEqual([]);
  });

  it('marca el origen de cada fila', () => {
    const filas = fusionarHistorial([localRec()], [servidorRec()]);
    expect(filas.map(f => f.origen).sort()).toEqual(['local', 'servidor']);
  });

  it('mapea el registro del servidor a la forma de la UI', () => {
    const [fila] = fusionarHistorial([], [servidorRec()]);
    expect(fila).toMatchObject({
      number: '900123456',
      timestamp: '2026-10-12 08:33:00', // ya convertido a local
      reason: 'Manolo (Modo 2/3)',
      action: 'Atendida por Manolo',
      origen: 'servidor',
      duracion: 42,
    });
  });

  it('una llamada en curso se etiqueta "En curso"', () => {
    const [fila] = fusionarHistorial([], [servidorRec({ estado: 'activa' })]);
    expect(fila.action).toBe('En curso');
  });

  it('deja el historial local intacto, solo añade origen y epoch', () => {
    const [fila] = fusionarHistorial([localRec()], []);
    expect(fila).toMatchObject({
      number: '902222333',
      timestamp: '2026-10-12 09:00:00',
      reason: 'Blacklist',
      action: 'Answer+Hangup',
      origen: 'local',
    });
  });

  it('coloca la fila del servidor según su hora local, no su hora UTC', () => {
    // Este es el caso que estaba roto, y lo que lo arregla es la conversión de
    // zona, no el criterio de orden: el del servidor a las 06:33 UTC son las
    // 08:33 locales, así que va DESPUÉS del local de las 09:00. Antes se
    // comparaba la cadena cruda "2026-10-12T06:33..." y salía primero.
    //
    // Verificado por mutación: con las dos fuentes ya en el mismo formato local,
    // ordenar por cadena o por epoch da el mismo resultado. El sort por epoch se
    // mantiene porque no depende del formato, pero no es lo que cubre este test.
    const filas = fusionarHistorial(
      [localRec({ number: 'LOCAL-09:00', timestamp: '2026-10-12 09:00:00' })],
      [servidorRec({ numero: 'SRV-08:33', timestamp_inicio: '2026-10-12T06:33:00+00:00' })]
    );
    expect(filas.map(f => f.number)).toEqual(['LOCAL-09:00', 'SRV-08:33']);
  });

  it('entrelaza correctamente varias filas de las dos fuentes', () => {
    const filas = fusionarHistorial(
      [
        localRec({ number: 'L-10:00', timestamp: '2026-10-12 10:00:00' }),
        localRec({ number: 'L-08:00', timestamp: '2026-10-12 08:00:00' }),
      ],
      [
        // 07:30 UTC = 09:30 local, 05:00 UTC = 07:00 local
        servidorRec({ numero: 'S-09:30', timestamp_inicio: '2026-10-12T07:30:00+00:00' }),
        servidorRec({ numero: 'S-07:00', timestamp_inicio: '2026-10-12T05:00:00+00:00' }),
      ]
    );
    expect(filas.map(f => f.number)).toEqual([
      'L-10:00',
      'S-09:30',
      'L-08:00',
      'S-07:00',
    ]);
  });
});
