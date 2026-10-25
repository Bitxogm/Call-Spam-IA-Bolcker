import { describe, it, expect, vi, beforeEach } from 'vitest';

const { check, request, platform } = vi.hoisted(() => ({
  check: vi.fn(),
  request: vi.fn(),
  platform: { OS: 'android', Version: 33 as number | string },
}));

vi.mock('react-native', () => ({
  Platform: platform,
  PermissionsAndroid: {
    PERMISSIONS: { POST_NOTIFICATIONS: 'android.permission.POST_NOTIFICATIONS' },
    RESULTS: { GRANTED: 'granted', DENIED: 'denied', NEVER_ASK_AGAIN: 'never_ask_again' },
    check,
    request,
  },
}));

import { pedirPermisoNotificaciones } from './permisoNotificaciones';

beforeEach(() => {
  vi.resetAllMocks();
  platform.OS = 'android';
  platform.Version = 33;
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

describe('pedirPermisoNotificaciones', () => {
  it('en Android 12 o anterior no pide nada', async () => {
    platform.Version = 32;
    expect(await pedirPermisoNotificaciones()).toBe(true);
    expect(check).not.toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
  });

  it('en Android 13 con el permiso ya concedido no pregunta', async () => {
    check.mockResolvedValue(true);
    expect(await pedirPermisoNotificaciones()).toBe(true);
    expect(request).not.toHaveBeenCalled();
  });

  it('en Android 13 sin el permiso lo pide', async () => {
    check.mockResolvedValue(false);
    request.mockResolvedValue('granted');
    expect(await pedirPermisoNotificaciones()).toBe(true);
    expect(request).toHaveBeenCalledWith('android.permission.POST_NOTIFICATIONS');
  });

  it.each(['denied', 'never_ask_again'])('si se responde %s devuelve false', async (resultado) => {
    check.mockResolvedValue(false);
    request.mockResolvedValue(resultado);
    expect(await pedirPermisoNotificaciones()).toBe(false);
  });

  it('un error no rompe el cambio de modo', async () => {
    check.mockRejectedValue(new Error('boom'));
    expect(await pedirPermisoNotificaciones()).toBe(false);
  });
});
