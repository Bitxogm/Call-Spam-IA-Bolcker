// src/services/BackendSyncService.ts
import { Alert } from 'react-native';

// Sin fallback a una IP real: el repo es público. Si falta la variable, las
// peticiones fallan de forma legible en vez de apuntar a un host hardcodeado.
const VPS_IP = process.env.EXPO_PUBLIC_VPS_IP;
const API_PORT = process.env.EXPO_PUBLIC_VPS_PORT || '5000';

if (!VPS_IP) {
  console.warn(
    '⚠️ EXPO_PUBLIC_VPS_IP no está definida: los Modos 2 y 3 no podrán ' +
    'sincronizar con el servidor. Añádela al .env y recompila.'
  );
}

/** Registro de llamada atendida por Manolo en el VPS (call_log.json) */
export interface ServerCallRecord {
  id: string;
  numero: string;
  timestamp_inicio: string;
  timestamp_fin: string | null;
  duracion_segundos: number;
  estado: 'activa' | 'finalizada';
}

class BackendSyncService {
  /**
   * Sincroniza el modo activo con el servidor Asterisk
   */
  syncMode = async (mode: 'FIXED' | 'AI') => {
    try {
      console.log(`🔄 Sincronizando modo ${mode} con el servidor...`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000); // 5s timeout

      const response = await fetch(`http://${VPS_IP}:${API_PORT}/set_mode`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ mode }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (response.ok) {
        console.log('✅ Modo sincronizado correctamente con el servidor');
      } else {
        console.error('❌ Error sincronizando con el servidor:', response.status);
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.error('❌ Timeout sincronizando con el servidor (5s)');
      } else {
        console.error('❌ Error de red sincronizando con el servidor:', error);
      }
    }
  };

  /**
   * Descarga el historial de llamadas atendidas por Manolo (Modos 2/3).
   * Devuelve [] si el VPS no responde: el historial local debe seguir viéndose.
   */
  getCallLog = async (): Promise<ServerCallRecord[]> => {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const response = await fetch(`http://${VPS_IP}:${API_PORT}/call_log`, {
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        console.error('❌ /call_log respondió', response.status);
        return [];
      }

      return await response.json();
    } catch (error: any) {
      console.error('❌ Error obteniendo call_log:', error?.name ?? error);
      return [];
    }
  };
}

export const backendSyncService = new BackendSyncService();
export default backendSyncService;
