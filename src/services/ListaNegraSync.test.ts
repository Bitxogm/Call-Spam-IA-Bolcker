import { describe, it, expect, vi, beforeEach } from 'vitest';

const { prefs, db } = vi.hoisted(() => ({
  prefs: {
    getAllNumbers: vi.fn(),
    addNumber: vi.fn(),
    removeNumber: vi.fn(),
  },
  db: {
    getSpamNumbers: vi.fn(),
    addSpamNumber: vi.fn(),
    deleteSpamNumber: vi.fn(),
  },
}));

// Los dos servicios reales cargan módulos nativos (BlacklistModule, expo-sqlite)
vi.mock('./BlacklistService', () => ({ blacklistService: prefs }));
vi.mock('./DataBaseService', () => ({ databaseService: db }));

import { sincronizarListaNegra, desbloquearNumero, MOTIVO_DESDE_JAVA } from './ListaNegraSync';

beforeEach(() => {
  vi.resetAllMocks();
  prefs.addNumber.mockResolvedValue(true);
  prefs.removeNumber.mockResolvedValue(true);
  db.addSpamNumber.mockResolvedValue(true);
  db.deleteSpamNumber.mockResolvedValue(true);
  vi.spyOn(console, 'log').mockImplementation(() => {});
});

describe('sincronizarListaNegra', () => {
  it('Java → pantalla: lo bloqueado por "Pasar y bloquear" se añade a la lista', async () => {
    prefs.getAllNumbers.mockResolvedValue(['622696266']);
    db.getSpamNumbers.mockResolvedValue([]);
    expect(await sincronizarListaNegra()).toEqual({ aLaPantalla: 1, alBloqueo: 0 });
    expect(db.addSpamNumber).toHaveBeenCalledWith('622696266', MOTIVO_DESDE_JAVA, 'auto');
    expect(prefs.addNumber).not.toHaveBeenCalled();
  });

  it('pantalla → Java: lo que se ve en la lista se bloquea de verdad', async () => {
    prefs.getAllNumbers.mockResolvedValue([]);
    db.getSpamNumbers.mockResolvedValue([{ id: 1, number: '+34900111222' }]);
    expect(await sincronizarListaNegra()).toEqual({ aLaPantalla: 0, alBloqueo: 1 });
    expect(prefs.addNumber).toHaveBeenCalledWith('+34900111222');
    expect(db.addSpamNumber).not.toHaveBeenCalled();
  });

  it('si ya coinciden no toca nada', async () => {
    prefs.getAllNumbers.mockResolvedValue(['900111222']);
    db.getSpamNumbers.mockResolvedValue([{ id: 1, number: '+34 900 111 222' }]);
    expect(await sincronizarListaNegra()).toEqual({ aLaPantalla: 0, alBloqueo: 0 });
    expect(prefs.addNumber).not.toHaveBeenCalled();
    expect(db.addSpamNumber).not.toHaveBeenCalled();
  });
});

describe('desbloquearNumero', () => {
  it('lo quita del filtro nativo y de todas sus filas, en cualquier formato', async () => {
    db.getSpamNumbers.mockResolvedValue([
      { id: 1, number: '+34622696266' },
      { id: 2, number: '622696266' },
      { id: 3, number: '900111222' },
    ]);
    expect(await desbloquearNumero('622696266')).toBe(true);
    expect(prefs.removeNumber).toHaveBeenCalledWith('622696266');
    expect(db.deleteSpamNumber.mock.calls.map(c => c[0])).toEqual([1, 2]);
  });

  it('si falla un lado, devuelve false', async () => {
    db.getSpamNumbers.mockResolvedValue([{ id: 1, number: '622696266' }]);
    prefs.removeNumber.mockResolvedValue(false);
    expect(await desbloquearNumero('622696266')).toBe(false);
  });

  it('tras desbloquear, la sincronización no lo vuelve a bloquear', async () => {
    // Estado después del borrado: ninguna de las dos fuentes lo tiene
    prefs.getAllNumbers.mockResolvedValue([]);
    db.getSpamNumbers.mockResolvedValue([]);
    expect(await sincronizarListaNegra()).toEqual({ aLaPantalla: 0, alBloqueo: 0 });
  });
});
