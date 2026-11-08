// src/services/ListaNegraSync.ts
import { blacklistService } from './BlacklistService';
import { databaseService } from './DataBaseService';
import { planSincronizacion, filasDelNumero } from '../utils/listaNegra';

export const MOTIVO_DESDE_JAVA = 'Bloqueado desde una llamada';

/**
 * Deja las dos fuentes iguales (ver utils/listaNegra.ts). Se llama al abrir
 * "Gestionar números" y después de cada borrado.
 */
export const sincronizarListaNegra = async (): Promise<{ aLaPantalla: number; alBloqueo: number }> => {
  const [enPrefs, enPantalla] = await Promise.all([
    blacklistService.getAllNumbers(),
    databaseService.getSpamNumbers(),
  ]);
  const plan = planSincronizacion(enPrefs, enPantalla);

  for (const numero of plan.aLaPantalla) {
    await databaseService.addSpamNumber(numero, MOTIVO_DESDE_JAVA, 'auto');
  }
  for (const numero of plan.alBloqueo) {
    await blacklistService.addNumber(numero);
  }
  if (plan.aLaPantalla.length || plan.alBloqueo.length) {
    console.log(`🔄 Lista negra sincronizada: +${plan.aLaPantalla.length} en pantalla, ` +
                `+${plan.alBloqueo.length} en el bloqueo`);
  }
  return { aLaPantalla: plan.aLaPantalla.length, alBloqueo: plan.alBloqueo.length };
};

/** Desbloquea de verdad: fuera del filtro nativo y todas sus filas de la pantalla. */
export const desbloquearNumero = async (numero: string): Promise<boolean> => {
  const enPantalla = await databaseService.getSpamNumbers();
  const resultados = await Promise.all([
    blacklistService.removeNumber(numero),
    ...filasDelNumero(enPantalla, numero).map(r => databaseService.deleteSpamNumber(r.id)),
  ]);
  return resultados.every(Boolean);
};
