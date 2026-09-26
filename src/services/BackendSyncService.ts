// src/services/BackendSyncService.ts

// URL completa, no IP + puerto: el servidor está detrás de Cloudflare → Nginx
// Proxy Manager, y en release Android bloquea el tráfico en claro
// (usesCleartextTraffic solo está en el manifest de debug). Tiene que ser https.
const BASE_URL = process.env.EXPO_PUBLIC_VPS_URL;
const TOKEN = process.env.EXPO_PUBLIC_CONTROL_API_TOKEN;

if (!BASE_URL || !TOKEN) {
  console.warn(
    '⚠️ Falta EXPO_PUBLIC_VPS_URL o EXPO_PUBLIC_CONTROL_API_TOKEN: los Modos 2 ' +
    'y 3 no podrán sincronizar. Añádelas al .env y recompila.'
  );
}

const headers = () => ({
  'Content-Type': 'application/json',
  'X-API-Key': TOKEN ?? '',
});

/** Un 401 no es "el VPS está caído": es token mal configurado. Que se vea. */
const avisarSi401 = (status: number, endpoint: string) => {
  if (status === 401) {
    console.error(
      `❌ 401 en ${endpoint}: el token de la app no coincide con el del VPS ` +
      '(revisa CONTROL_API_TOKEN en el servidor y EXPO_PUBLIC_CONTROL_API_TOKEN en el .env)'
    );
  }
};

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

      const response = await fetch(`${BASE_URL}/set_mode`, {
        method: 'POST',
        headers: headers(),
        body: JSON.stringify({ mode }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (response.ok) {
        console.log('✅ Modo sincronizado correctamente con el servidor');
      } else {
        avisarSi401(response.status, '/set_mode');
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

      const response = await fetch(`${BASE_URL}/call_log`, {
        headers: headers(),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        avisarSi401(response.status, '/call_log');
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
