// src/utils/historial.ts
//
// Lógica pura del historial, extraída de CallHistoryScreen para poder testearla
// sin montar React Native ni mockear tres servicios.
//
// `import type` es deliberado: CallHistoryService importa NativeModules de
// react-native, y un import normal arrastraría RN al test. Con `import type`
// TypeScript lo borra al compilar y Vitest no lo carga.
import type { SpamCallRecord } from '../services/CallHistoryService';
import type { ServerCallRecord } from '../services/BackendSyncService';

/** Fila del historial, con el origen para diferenciarla visualmente */
export type HistoryRow = SpamCallRecord & {
  origen: 'local' | 'servidor';
  duracion?: number;
  /** Instante real, para ordenar. `timestamp` es solo para mostrar. */
  epoch: number;
};

/**
 * El servidor manda ISO 8601 en UTC con offset. Los registros anteriores al
 * cambio vienen sin offset: eran UTC igualmente, así que se les añade la Z
 * antes de parsear — si no, JS los interpretaría como hora local.
 */
export const parseServidor = (iso: string): Date => {
  const conZona = /(Z|[+-]\d{2}:?\d{2})$/.test(iso) ? iso : `${iso}Z`;
  return new Date(conZona);
};

/** "yyyy-MM-dd HH:mm:ss" en hora local, igual que CallHistoryHelper.java */
export const formatearLocal = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
         `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

/**
 * Qué modo atendió la llamada. Los registros escritos antes del 24/10/2026
 * no traen `modo`, y para esos se mantiene la etiqueta genérica.
 */
export const etiquetaServidor = (r: ServerCallRecord): string => {
  if (r.modo === 'FIXED') return 'Manolo (Modo 2)';
  if (r.modo === 'AI') {
    // plan_b acumula los saltos: "Cartesia", "Cartesia → TTS", "Deepgram"...
    const saltos = r.plan_b ? r.plan_b.split(' → ') : [];
    if (saltos.some(p => p !== 'Cartesia')) return 'Manolo (Modo 3 → mensaje fijo)';
    if (saltos.length > 0) return 'Manolo (Modo 3 · voz de reserva)';
    return 'Manolo (Modo 3)';
  }
  return 'Manolo (Modo 2/3)';
};

/**
 * Une el historial local (Modo 1, ya en hora del dispositivo) con el del
 * servidor (Modos 2/3, UTC) y ordena por instante, no por cadena: las dos
 * fuentes venían en zonas distintas y comparar el texto mezclaba el orden.
 */
export const fusionarHistorial = (
  localData: SpamCallRecord[],
  serverData: ServerCallRecord[]
): HistoryRow[] => {
  const locales: HistoryRow[] = localData.map(r => ({
    ...r,
    origen: 'local' as const,
    epoch: new Date(r.timestamp.replace(' ', 'T')).getTime(),
  }));

  const servidor: HistoryRow[] = serverData.map(r => {
    const d = parseServidor(r.timestamp_inicio);
    return {
      number: r.numero,
      timestamp: formatearLocal(d),
      reason: etiquetaServidor(r),
      action: r.estado === 'activa' ? 'En curso' : 'Atendida por Manolo',
      origen: 'servidor' as const,
      duracion: r.duracion_segundos,
      epoch: d.getTime(),
    };
  });

  return [...locales, ...servidor].sort((a, b) => b.epoch - a.epoch);
};
