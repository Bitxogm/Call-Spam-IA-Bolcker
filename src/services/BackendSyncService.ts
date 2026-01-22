// src/services/BackendSyncService.ts
import { Alert } from 'react-native';

const VPS_IP = '157.180.35.161';
const API_PORT = '5000'; // Puerto para nuestra pequeña API de control

class BackendSyncService {
  /**
   * Sincroniza el modo activo con el servidor Asterisk
   */
  syncMode = async (mode: 'FIXED' | 'AI') => {
    try {
      console.log(`🔄 Sincronizando modo ${mode} con el servidor...`);

      // Implementaremos una pequeña API en Flask en el servidor para esto
      const response = await fetch(`http://${VPS_IP}:${API_PORT}/set_mode`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ mode }),
      });

      if (response.ok) {
        console.log('✅ Modo sincronizado correctamente con el servidor');
      } else {
        console.error('❌ Error sincronizando con el servidor:', response.status);
      }
    } catch (error) {
      console.error('❌ Error de red sincronizando con el servidor:', error);
      // No mostramos Alert aquí para no molestar al usuario si no tiene internet en ese momento
      // El servidor debería tener un default (ej: AI)
    }
  };
}

export const backendSyncService = new BackendSyncService();
export default backendSyncService;
