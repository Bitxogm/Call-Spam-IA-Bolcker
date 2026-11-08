import { describe, it, expect, vi, beforeEach } from 'vitest';

const { getLogs } = vi.hoisted(() => ({ getLogs: vi.fn() }));

vi.mock('react-native', () => ({
  NativeModules: { LogsModule: { getLogs, logInfo: vi.fn() } },
}));

import LogsService from './LogsService';

beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

describe('getLogsDetallado', () => {
  it('devuelve las líneas sin error', async () => {
    const lineas = [{ timestamp: '2026-11-08 08:10:00', level: 'INFO', message: 'hola' }];
    getLogs.mockResolvedValue(lineas);
    expect(await LogsService.getLogsDetallado()).toEqual({ logs: lineas, error: null });
  });

  it('si el módulo falla, devuelve el error en vez de una lista vacía sin más', async () => {
    getLogs.mockRejectedValue(new Error('GET_LOGS_ERROR: boom'));
    expect(await LogsService.getLogsDetallado()).toEqual({ logs: [], error: 'GET_LOGS_ERROR: boom' });
  });

  it('si no devuelve una lista, lo dice', async () => {
    getLogs.mockResolvedValue(null);
    const { error } = await LogsService.getLogsDetallado();
    expect(error).toContain('no devolvió una lista');
  });

  it('getLogs sigue devolviendo [] si falla (para quien no quiera el error)', async () => {
    getLogs.mockRejectedValue(new Error('boom'));
    expect(await LogsService.getLogs()).toEqual([]);
  });
});

describe('sin el módulo nativo', () => {
  it('lo dice en vez de quedarse en blanco', async () => {
    vi.resetModules();
    vi.doMock('react-native', () => ({ NativeModules: {} }));
    const { default: sinModulo } = await import('./LogsService');
    const { error } = await sinModulo.getLogsDetallado();
    expect(error).toContain('LogsModule no está disponible');
    vi.doUnmock('react-native');
  });
});
