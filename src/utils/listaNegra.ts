// src/utils/listaNegra.ts
//
// La lista negra vive en dos sitios: SharedPreferences (lo que mira el filtro nativo
// para bloquear) y SQLite (lo que pinta "Gestionar números"). Para el usuario tiene
// que ser una sola: esto calcula lo que falta en cada lado. Lógica pura, sin RN.

/** Igual que SharedPreferencesHelper.normalizePhoneNumber: solo dígitos, los 9 últimos. */
export const normalizarNumero = (numero: string): string => {
  const digitos = (numero ?? '').replace(/\D/g, '');
  return digitos.length > 9 ? digitos.slice(-9) : digitos;
};

export type PlanSincronizacion = {
  /** Bloqueados desde Java ("Pasar y bloquear"…) que no aparecen en la pantalla */
  aLaPantalla: string[];
  /** En la pantalla pero sin bloquear de verdad */
  alBloqueo: string[];
};

export const planSincronizacion = (
  enPrefs: string[],
  enPantalla: { number: string }[]
): PlanSincronizacion => {
  const prefs = new Set(enPrefs.map(normalizarNumero).filter(Boolean));
  const pantalla = new Set(enPantalla.map(r => normalizarNumero(r.number)).filter(Boolean));

  const alBloqueo: string[] = [];
  const vistos = new Set<string>();
  for (const r of enPantalla) {
    const n = normalizarNumero(r.number);
    if (n && !prefs.has(n) && !vistos.has(n)) {
      vistos.add(n);
      alBloqueo.push(r.number);
    }
  }

  return {
    aLaPantalla: [...prefs].filter(n => !pantalla.has(n)),
    alBloqueo,
  };
};

/**
 * Filas de la pantalla que corresponden a un número, en cualquier formato: al
 * desbloquear hay que quitarlas todas, o la siguiente sincronización lo volvería a
 * bloquear desde la que quedase.
 */
export const filasDelNumero = <T extends { id: number; number: string }>(
  enPantalla: T[],
  numero: string
): T[] => {
  const n = normalizarNumero(numero);
  return n ? enPantalla.filter(r => normalizarNumero(r.number) === n) : [];
};
