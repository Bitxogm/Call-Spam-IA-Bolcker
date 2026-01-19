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
  getMode = async (): Promise<'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'REJECT_TO_BACKEND' | 'AI_CONVERSATION'> => {
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
  setMode = async (mode: 'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'REJECT_TO_BACKEND' | 'AI_CONVERSATION'): Promise<boolean> => {
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
    mode: 'HANGUP_IMMEDIATELY' | 'PLAY_MESSAGE' | 'REJECT_TO_BACKEND' | 'AI_CONVERSATION';
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

  /**
   * Verifica permisos críticos para Answer+Hangup
   *
   * IMPORTANTE: En Android 9+, se requiere READ_CALL_LOG para recibir broadcasts
   * de PHONE_STATE con detalles (RINGING, OFFHOOK). Sin este permiso, solo se
   * reciben broadcasts de IDLE, y Answer+Hangup NO funcionará.
   */
  checkCallLogPermission = async (): Promise<{
    hasPhoneState: boolean;
    hasCallLog: boolean;
    hasAnswerCalls: boolean;
    allGranted: boolean;
  }> => {
    try {
      const result = await AnswerHangupModule.checkCallLogPermission();
      return result;
    } catch (error) {
      console.error('❌ Error verificando permisos:', error);
      return {
        hasPhoneState: false,
        hasCallLog: false,
        hasAnswerCalls: false,
        allGranted: false,
      };
    }
  };

  /**
   * Verifica si el servicio de Accesibilidad está habilitado
   *
   * CRÍTICO: Para que funcione Modo 2 (IVR) y Modo 3 (IA), necesitamos
   * Accessibility Service que permita auto-contestar llamadas.
   */
  isAccessibilityServiceEnabled = async (): Promise<boolean> => {
    try {
      const isEnabled = await AnswerHangupModule.isAccessibilityServiceEnabled();
      return isEnabled;
    } catch (error) {
      console.error('❌ Error verificando Accessibility Service:', error);
      return false;
    }
  };

  /**
   * Abre la configuración de Accesibilidad
   */
  openAccessibilitySettings = async (): Promise<boolean> => {
    try {
      await AnswerHangupModule.openAccessibilitySettings();
      return true;
    } catch (error) {
      console.error('❌ Error abriendo configuración de Accesibilidad:', error);
      return false;
    }
  };

  /**
   * Habilita SpamCallService (InCallService) programáticamente
   *
   * CRÍTICO: Necesario para que Android permita que el servicio se inicie
   */
  enableInCallService = async (): Promise<boolean> => {
    try {
      await AnswerHangupModule.enableInCallService();
      console.log('✅ SpamCallService habilitado programáticamente');
      return true;
    } catch (error) {
      console.error('❌ Error habilitando SpamCallService:', error);
      return false;
    }
  };

  /**
   * Verifica el estado de SpamCallService
   */
  checkInCallServiceStatus = async (): Promise<{
    stateCode: number;
    stateName: string;
    isEnabled: boolean;
    isDisabled: boolean;
    isDefault: boolean;
  }> => {
    try {
      const status = await AnswerHangupModule.checkInCallServiceStatus();
      console.log(`🔍 Estado de SpamCallService: ${status.stateName} (${status.stateCode})`);
      return status;
    } catch (error) {
      console.error('❌ Error verificando estado de SpamCallService:', error);
      return {
        stateCode: -1,
        stateName: 'ERROR',
        isEnabled: false,
        isDisabled: false,
        isDefault: false,
      };
    }
  };

  /**
   * Prueba si un número sería bloqueado por los prefijos 800/900
   * TESTING: Para verificar que la lógica de bloqueo funciona
   */
  testPrefixBlocking = async (phoneNumber: string): Promise<{
    originalNumber: string;
    cleanedNumber: string;
    wouldBeBlocked: boolean;
    matchedPrefix: string;
    blockReason: string;
  }> => {
    try {
      const result = await AnswerHangupModule.testPrefixBlocking(phoneNumber);
      console.log(`🧪 Test de bloqueo para ${phoneNumber}:`, result);
      return result;
    } catch (error) {
      console.error('❌ Error en testPrefixBlocking:', error);
      throw error;
    }
  };
}

export const answerHangupService = new AnswerHangupService();
export default answerHangupService;
