// src/services/BackendSyncService.ts
import { Alert } from 'react-native';

const VPS_IP = process.env.EXPO_PUBLIC_VPS_IP || '157.180.35.161';
const API_PORT = process.env.EXPO_PUBLIC_VPS_PORT || '5000';

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
   * Envía el texto para generar el audio del Mensaje Fijo
   */
  syncMessage = async (text: string) => {
    try {
      console.log(`🔄 Sincronizando mensaje TTS con el servidor: "${text}"`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 15000); // 15s timeout para audio

      const response = await fetch(`http://${VPS_IP}:${API_PORT}/set_message`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ text }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`Server error: ${response.status}`);
      }

      console.log('✅ Mensaje TTS sincronizado y generado en el servidor');
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.error('❌ Timeout sincronizando mensaje con el servidor');
      } else {
        console.error('❌ Error sincronizando mensaje con el servidor:', error);
      }
      throw error;
    }
  };
}

export const backendSyncService = new BackendSyncService();
export default backendSyncService;
