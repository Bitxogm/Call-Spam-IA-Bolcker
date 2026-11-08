import { describe, it, expect, vi, beforeEach } from 'vitest';

const { getInfo } = vi.hoisted(() => ({ getInfo: vi.fn() }));

vi.mock('react-native', () => ({ NativeModules: { BuildInfoModule: { getInfo } } }));

import { getBuildInfo, textoSello, mostrarHerramientasDebug } from './BuildInfoService';

beforeEach(() => vi.resetAllMocks());

describe('getBuildInfo', () => {
  it('devuelve lo que da el módulo', async () => {
    getInfo.mockResolvedValue({ buildTime: '08/11/2026 12:40:00', debug: true });
    expect(await getBuildInfo()).toEqual({ buildTime: '08/11/2026 12:40:00', debug: true });
  });

  it('si el módulo falla, null', async () => {
    getInfo.mockRejectedValue(new Error('boom'));
    expect(await getBuildInfo()).toBeNull();
  });
});

describe('textoSello', () => {
  const info = { buildTime: '08/11/2026 12:40:00', debug: true };

  it('APK nuevo con su propio JS', () => {
    expect(textoSello(info, false)).toBe('APK compilado: 08/11/2026 12:40:00 · JS del APK');
  });

  it('avisa si el JS no es el del APK', () => {
    expect(textoSello(info, true)).toContain('JS de Metro/caché');
  });

  it('APK viejo sin módulo', () => {
    expect(textoSello(null, false)).toContain('APK sin sello');
  });
});

describe('mostrarHerramientasDebug', () => {
  it('en un APK debug aunque el bundle sea de producción', () => {
    expect(mostrarHerramientasDebug({ buildTime: 'x', debug: true }, false)).toBe(true);
  });

  it('no en release con bundle de producción', () => {
    expect(mostrarHerramientasDebug({ buildTime: 'x', debug: false }, false)).toBe(false);
    expect(mostrarHerramientasDebug(null, false)).toBe(false);
  });
});
