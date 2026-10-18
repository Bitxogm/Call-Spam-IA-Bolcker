import { describe, it, expect, vi, beforeEach } from 'vitest';

const { check, request, configureForMode } = vi.hoisted(() => ({
  check: vi.fn(),
  request: vi.fn(),
  configureForMode: vi.fn(),
}));

// El react-native real no carga en Node: basta con lo que usa el servicio
vi.mock('react-native', () => ({
  NativeModules: { CallForwardingModule: { configureForMode } },
  PermissionsAndroid: {
    PERMISSIONS: { CALL_PHONE: 'android.permission.CALL_PHONE' },
    RESULTS: { GRANTED: 'granted', DENIED: 'denied', NEVER_ASK_AGAIN: 'never_ask_again' },
    check,
    request,
  },
}));

import callForwardingService from './CallForwardingService';

beforeEach(() => {
  vi.resetAllMocks();
  configureForMode.mockResolvedValue(true);
  vi.spyOn(console, 'log').mockImplementation(() => {});
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

describe('configureForMode y CALL_PHONE', () => {
  it('con el permiso ya concedido no pregunta y ejecuta el USSD', async () => {
    check.mockResolvedValue(true);

    expect(await callForwardingService.configureForMode('BACKEND_FIXED')).toBe(true);
    expect(request).not.toHaveBeenCalled();
    expect(configureForMode).toHaveBeenCalledWith('BACKEND_FIXED');
  });

  it('sin el permiso lo pide y, si se concede, ejecuta el USSD', async () => {
    check.mockResolvedValue(false);
    request.mockResolvedValue('granted');

    expect(await callForwardingService.configureForMode('BACKEND_AI')).toBe(true);
    expect(request).toHaveBeenCalledWith('android.permission.CALL_PHONE');
    expect(configureForMode).toHaveBeenCalledWith('BACKEND_AI');
  });

  it.each(['denied', 'never_ask_again'])('si se responde %s no llama al módulo nativo', async (resultado) => {
    check.mockResolvedValue(false);
    request.mockResolvedValue(resultado);

    expect(await callForwardingService.configureForMode('BACKEND_FIXED')).toBe(false);
    expect(configureForMode).not.toHaveBeenCalled();
  });

  it('el Modo 1 también lo comprueba: ##67# sale por el mismo placeCall', async () => {
    check.mockResolvedValue(false);
    request.mockResolvedValue('granted');

    await callForwardingService.configureForMode('HANGUP_IMMEDIATELY');
    expect(request).toHaveBeenCalledOnce();
    expect(configureForMode).toHaveBeenCalledWith('HANGUP_IMMEDIATELY');
  });
});
