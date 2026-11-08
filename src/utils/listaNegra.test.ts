import { describe, it, expect } from 'vitest';
import { normalizarNumero, planSincronizacion, filasDelNumero } from './listaNegra';

const fila = (id: number, number: string) => ({ id, number });

describe('normalizarNumero (igual que SharedPreferencesHelper)', () => {
  it('solo dígitos y los 9 últimos', () => {
    expect(normalizarNumero('+34 622 69 62 66')).toBe('622696266');
    expect(normalizarNumero('0034622696266')).toBe('622696266');
    expect(normalizarNumero('900-123-456')).toBe('900123456');
    expect(normalizarNumero('Desconocido')).toBe('');
  });
});

describe('planSincronizacion', () => {
  it('Java → pantalla: lo bloqueado desde "Pasar y bloquear" aparece en la lista', () => {
    const plan = planSincronizacion(['622696266'], []);
    expect(plan.aLaPantalla).toEqual(['622696266']);
    expect(plan.alBloqueo).toEqual([]);
  });

  it('pantalla → Java: lo que se ve en la lista pero no bloquea, se bloquea', () => {
    const plan = planSincronizacion([], [fila(1, '+34 900 111 222')]);
    expect(plan.alBloqueo).toEqual(['+34 900 111 222']);
    expect(plan.aLaPantalla).toEqual([]);
  });

  it('el mismo número en formatos distintos no se duplica', () => {
    const plan = planSincronizacion(['622696266'], [fila(1, '+34622696266')]);
    expect(plan).toEqual({ aLaPantalla: [], alBloqueo: [] });
  });

  it('las dos a la vez', () => {
    const plan = planSincronizacion(['622696266', '900111222'], [fila(1, '900111222'), fila(2, '911222333')]);
    expect(plan).toEqual({ aLaPantalla: ['622696266'], alBloqueo: ['911222333'] });
  });

  it('un número vacío (oculto) no se sincroniza en ningún sentido', () => {
    expect(planSincronizacion([''], [fila(1, 'Desconocido')])).toEqual({ aLaPantalla: [], alBloqueo: [] });
  });

  it('dos filas del mismo número sin bloquear se bloquean una sola vez', () => {
    const plan = planSincronizacion([], [fila(1, '+34900111222'), fila(2, '900111222')]);
    expect(plan.alBloqueo).toEqual(['+34900111222']);
  });
});

describe('filasDelNumero', () => {
  it('encuentra todas las filas del número, en cualquier formato', () => {
    const filas = [fila(1, '+34 622 69 62 66'), fila(2, '622696266'), fila(3, '900111222')];
    expect(filasDelNumero(filas, '622696266').map(f => f.id)).toEqual([1, 2]);
  });

  it('un número vacío no se lleva ninguna fila', () => {
    expect(filasDelNumero([fila(1, 'Desconocido')], 'Desconocido')).toEqual([]);
  });
});
