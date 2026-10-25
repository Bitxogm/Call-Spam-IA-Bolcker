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
  // Python escribe microsegundos (.233625). V8 los acepta, pero el estándar solo
  // garantiza milisegundos: se recortan a 3 cifras para que Hermes no dé Invalid Date.
  const ms = iso.replace(/(\.\d{3})\d+/, '$1');
  const conZona = /(Z|[+-]\d{2}:?\d{2})$/.test(ms) ? ms : `${ms}Z`;
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
/** Voces premium: si solo cayó una de estas, el personaje siguió hablando con Edge. */
const VOCES_DE_RESERVA = ['Cartesia', 'ElevenLabs'];

/** 'paquita' → 'Paquita'. Los registros anteriores al 25/10/2026 no traen personaje: Manolo. */
const nombrePersonaje = (personaje?: string | null): string =>
  personaje ? personaje.charAt(0).toUpperCase() + personaje.slice(1) : 'Manolo';

export const etiquetaServidor = (r: ServerCallRecord): string => {
  if (r.modo === 'FIXED') return 'Manolo (Modo 2)';
  if (r.modo === 'AI') {
    const nombre = nombrePersonaje(r.personaje);
    // plan_b acumula los saltos: "Cartesia", "ElevenLabs → TTS", "Deepgram"...
    const saltos = r.plan_b ? r.plan_b.split(' → ') : [];
    if (saltos.some(p => !VOCES_DE_RESERVA.includes(p))) return `${nombre} (Modo 3 → mensaje fijo)`;
    if (saltos.length > 0) return `${nombre} (Modo 3 · voz de reserva)`;
    return `${nombre} (Modo 3)`;
  }
  return 'Manolo (Modo 2/3)';
};

const filaLocal = (r: SpamCallRecord): HistoryRow => {
  const epoch = new Date(r.timestamp.replace(' ', 'T')).getTime();
  if (Number.isNaN(epoch)) throw new Error(`fecha no válida: ${r.timestamp}`);
  return { ...r, origen: 'local' as const, epoch };
};

const filaServidor = (r: ServerCallRecord): HistoryRow => {
  const d = parseServidor(r.timestamp_inicio);
  if (Number.isNaN(d.getTime())) throw new Error(`fecha no válida: ${r.timestamp_inicio}`);
  return {
    number: r.numero,
    timestamp: formatearLocal(d),
    reason: etiquetaServidor(r),
    action: r.estado === 'activa' ? 'En curso' : 'Atendida por Manolo',
    origen: 'servidor' as const,
    duracion: r.duracion_segundos,
    epoch: d.getTime(),
  };
};

const mensajeDe = (e: unknown): string => (e instanceof Error ? e.message : String(e));

/** Filas que se pudieron montar y el error de cada registro que no. */
export type Fusion = { filas: HistoryRow[]; errores: string[] };

/**
 * Une el historial local (Modo 1, ya en hora del dispositivo) con el del
 * servidor (Modos 2/3, UTC) y ordena por instante, no por cadena: las dos
 * fuentes venían en zonas distintas y comparar el texto mezclaba el orden.
 *
 * Cada registro va por separado: uno raro se salta y se apunta en `errores`,
 * nunca vacía el historial entero (pasó el 25/10/2026).
 */
export const fusionarHistorialSeguro = (localData: unknown, serverData: unknown): Fusion => {
  const filas: HistoryRow[] = [];
  const errores: string[] = [];

  const recorrer = <T,>(datos: unknown, fuente: string, montar: (r: T) => HistoryRow, id: (r: T) => unknown) => {
    if (!Array.isArray(datos)) {
      errores.push(`${fuente}: no es una lista (${datos === null ? 'null' : typeof datos})`);
      return;
    }
    datos.forEach((r, i) => {
      try {
        filas.push(montar(r as T));
      } catch (e) {
        const etiqueta = (r && id(r as T)) || `#${i + 1}`;
        errores.push(`${fuente}, registro ${etiqueta}: ${mensajeDe(e)}`);
      }
    });
  };

  recorrer<SpamCallRecord>(localData, 'Local', filaLocal, r => r.timestamp);
  recorrer<ServerCallRecord>(serverData, 'Servidor', filaServidor, r => r.id);

  filas.sort((a, b) => b.epoch - a.epoch);
  return { filas, errores };
};

/** Igual que fusionarHistorialSeguro, solo las filas. */
export const fusionarHistorial = (
  localData: SpamCallRecord[],
  serverData: ServerCallRecord[]
): HistoryRow[] => fusionarHistorialSeguro(localData, serverData).filas;
