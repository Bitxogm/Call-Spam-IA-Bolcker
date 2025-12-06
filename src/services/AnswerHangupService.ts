// AnswerHangupService.ts
import { NativeModules } from 'react-native';

const { AnswerHangupModule } = NativeModules;

/**
 * Servicio para gestionar Answer+Hangup (contestar y colgar automáticamente)
 */
class AnswerHangupService {
  /**
   * Verifica si Answer+Hangup está activado
   */
  isEnabled = async (): Promise<boolean> => {
    try {
      const enabled = await AnswerHangupModule.isEnabled();
      return enabled;
    } catch (error) {
      console.error('❌ Error verificando Answer+Hangup:', error);
      return false;
    }
  };

  /**
   * Activa o desactiva Answer+Hangup
   */
  setEnabled = async (enabled: boolean): Promise<boolean> => {
    try {
      await AnswerHangupModule.setEnabled(enabled);
      console.log(`🔇 Answer+Hangup ${enabled ? 'ACTIVADO' : 'DESACTIVADO'}`);
      return true;
    } catch (error) {
      console.error('❌ Error configurando Answer+Hangup:', error);
      return false;
    }
  };

  /**
   * Obtiene el delay actual en segundos
   */
  getHangupDelay = async (): Promise<number> => {
    try {
      const delay = await AnswerHangupModule.getHangupDelay();
      return delay;
    } catch (error) {
      console.error('❌ Error obteniendo delay:', error);
      return 2; // Default
    }
  };

  /**
   * Configura el delay en segundos (1-5)
   */
  setHangupDelay = async (seconds: number): Promise<boolean> => {
    try {
      await AnswerHangupModule.setHangupDelay(seconds);
      console.log(`⏱️ Delay configurado: ${seconds} segundos`);
      return true;
    } catch (error) {
      console.error('❌ Error configurando delay:', error);
      return false;
    }
  };

  /**
   * Obtiene información de debug
   */
  getDebugInfo = async (): Promise<string> => {
    try {
      const info = await AnswerHangupModule.getDebugInfo();
      return info;
    } catch (error) {
      console.error('❌ Error obteniendo debug info:', error);
      return 'Error';
    }
  };
}

export default new AnswerHangupService();
