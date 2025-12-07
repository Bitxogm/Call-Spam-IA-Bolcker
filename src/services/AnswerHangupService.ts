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

  /**
   * Obtiene el modo actual
   */
  getMode = async (): Promise<'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION'> => {
    try {
      const mode = await AnswerHangupModule.getMode();
      return mode;
    } catch (error) {
      console.error('❌ Error obteniendo modo:', error);
      return 'HANGUP_IMMEDIATELY'; // Default
    }
  };

  /**
   * Configura el modo de Answer+Hangup
   */
  setMode = async (mode: 'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION'): Promise<boolean> => {
    try {
      await AnswerHangupModule.setMode(mode);
      console.log(`🎯 Modo configurado: ${mode}`);
      return true;
    } catch (error) {
      console.error('❌ Error configurando modo:', error);
      return false;
    }
  };

  /**
   * Obtiene toda la configuración de Answer+Hangup
   */
  getConfiguration = async (): Promise<{
    enabled: boolean;
    mode: 'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'AI_CONVERSATION';
    hangupDelay: number;
  }> => {
    try {
      const [enabled, mode, hangupDelay] = await Promise.all([
        this.isEnabled(),
        this.getMode(),
        this.getHangupDelay(),
      ]);

      return { enabled, mode, hangupDelay };
    } catch (error) {
      console.error('❌ Error obteniendo configuración:', error);
      return {
        enabled: false,
        mode: 'HANGUP_IMMEDIATELY',
        hangupDelay: 2,
      };
    }
  };
}

export const answerHangupService = new AnswerHangupService();
export default answerHangupService;
