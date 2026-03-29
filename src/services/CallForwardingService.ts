// src/services/CallForwardingService.ts
import { NativeModules } from 'react-native';

const { CallForwardingModule } = NativeModules;

type Mode = 'HANGUP_IMMEDIATELY' | 'BACKEND_FIXED' | 'BACKEND_AI';

class CallForwardingService {
  /**
   * Activa desvío incondicional a Zadarma
   * Ejecuta: *21*+34919933065#
   */
  enableForwarding = async (): Promise<boolean> => {
    try {
      console.log('🔄 Activando desvío a Zadarma...');
      const success = await CallForwardingModule.enableForwarding();
      if (success) {
        console.log('✅ Desvío activado');
      } else {
        console.warn('⚠️ No se pudo activar desvío automáticamente');
      }
      return success;
    } catch (error) {
      console.error('❌ Error activando desvío:', error);
      return false;
    }
  };

  /**
   * Desactiva desvío incondicional
   * Ejecuta: ##21#
   */
  disableForwarding = async (): Promise<boolean> => {
    try {
      console.log('🔄 Desactivando desvío...');
      const success = await CallForwardingModule.disableForwarding();
      if (success) {
        console.log('✅ Desvío desactivado');
      } else {
        console.warn('⚠️ No se pudo desactivar desvío automáticamente');
      }
      return success;
    } catch (error) {
      console.error('❌ Error desactivando desvío:', error);
      return false;
    }
  };

  /**
   * Consulta estado actual del desvío
   * Ejecuta: *#21#
   * La respuesta se mostrará en pantalla del sistema
   */
  checkForwardingStatus = async (): Promise<boolean> => {
    try {
      console.log('🔍 Consultando estado de desvío...');
      const success = await CallForwardingModule.checkForwardingStatus();
      return success;
    } catch (error) {
      console.error('❌ Error consultando estado:', error);
      return false;
    }
  };

  /**
   * Configura desvío automáticamente según el modo seleccionado
   *
   * - HANGUP_IMMEDIATELY → Desactiva desvío (##21#)
   * - BACKEND_FIXED → Activa desvío (*21*+34919933065#)
   * - BACKEND_AI → Activa desvío (*21*+34919933065#)
   */
  configureForMode = async (mode: Mode): Promise<boolean> => {
    try {
      console.log(`⚙️ Configurando desvío para modo: ${mode}`);
      const success = await CallForwardingModule.configureForMode(mode);

      if (success) {
        if (mode === 'HANGUP_IMMEDIATELY') {
          console.log('✅ Desvío desactivado (Modo 1)');
        } else {
          console.log(`✅ Desvío activado a Zadarma (${mode})`);
        }
      }

      return success;
    } catch (error) {
      console.error('❌ Error configurando desvío:', error);
      return false;
    }
  };

  /**
   * Obtiene el número Zadarma configurado
   */
  getZadarmaNumber = async (): Promise<string> => {
    try {
      const number = await CallForwardingModule.getZadarmaNumber();
      return number;
    } catch (error) {
      console.error('❌ Error obteniendo número Zadarma:', error);
      return '+34919933065';
    }
  };
}

export const callForwardingService = new CallForwardingService();
export default callForwardingService;
