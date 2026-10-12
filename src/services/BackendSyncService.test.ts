import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import backendSyncService from './BackendSyncService';

// BackendSyncService no importa nada de react-native, así que se prueba directo.
// La URL y el token los inyecta vitest.config.ts por env.
const TOKEN = 'token-de-test';

const respuesta = (status: number, body: unknown = {}) =>
  ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  vi.spyOn(console, 'error').mockImplementation(() => {});
  vi.spyOn(console, 'log').mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('getCallLog', () => {
  it('devuelve el array del servidor cuando responde 200', async () => {
    const registros = [{ id: 'x', numero: '900111222' }];
    fetchMock.mockResolvedValue(respuesta(200, registros));
    await expect(backendSyncService.getCallLog()).resolves.toEqual(registros);
  });

  it('devuelve [] con un 401 y lo distingue en el log', async () => {
    fetchMock.mockResolvedValue(respuesta(401));
    await expect(backendSyncService.getCallLog()).resolves.toEqual([]);
    expect(console.error).toHaveBeenCalledWith(
      expect.stringContaining('401'),
    );
  });

  it('devuelve [] con un 500', async () => {
    fetchMock.mockResolvedValue(respuesta(500));
    await expect(backendSyncService.getCallLog()).resolves.toEqual([]);
  });

  it('devuelve [] si la red falla — el historial local debe seguir viéndose', async () => {
    fetchMock.mockRejectedValue(Object.assign(new Error('timeout'), { name: 'AbortError' }));
    await expect(backendSyncService.getCallLog()).resolves.toEqual([]);
  });

  it('manda la cabecera X-API-Key', async () => {
    fetchMock.mockResolvedValue(respuesta(200, []));
    await backendSyncService.getCallLog();
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['X-API-Key']).toBe(TOKEN);
  });
});

describe('clearCallLog', () => {
  it('devuelve true con un 200 y usa el método DELETE', async () => {
    fetchMock.mockResolvedValue(respuesta(200, { status: 'success', borradas: 3 }));
    await expect(backendSyncService.clearCallLog()).resolves.toBe(true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('DELETE');
    expect(String(url)).toMatch(/\/call_log$/);
    expect(init.headers['X-API-Key']).toBe(TOKEN);
  });

  it('devuelve false con un 401 — la pantalla debe avisar del borrado parcial', async () => {
    fetchMock.mockResolvedValue(respuesta(401));
    await expect(backendSyncService.clearCallLog()).resolves.toBe(false);
  });

  it('devuelve false si la red falla', async () => {
    fetchMock.mockRejectedValue(new Error('ECONNREFUSED'));
    await expect(backendSyncService.clearCallLog()).resolves.toBe(false);
  });
});

describe('syncMode', () => {
  it('manda el modo en el body y la cabecera X-API-Key', async () => {
    fetchMock.mockResolvedValue(respuesta(200, { status: 'success', mode: 'FIXED' }));
    await backendSyncService.syncMode('FIXED');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/set_mode$/);
    expect(init.method).toBe('POST');
    expect(init.headers['X-API-Key']).toBe(TOKEN);
    expect(JSON.parse(init.body)).toEqual({ mode: 'FIXED' });
  });
});
