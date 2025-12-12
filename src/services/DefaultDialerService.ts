import { NativeModules } from 'react-native';

const { DefaultDialerModule } = NativeModules;

/**
 * Servicio para manejar la app de marcador predeterminada
 *
 * ¿Por qué necesitamos esto?
 * Samsung/Android bloquean InCallService para apps de terceros.
 * Solo la app de marcador predeterminada puede usar InCallService completamente.
 * Esto permite que el audio IVR se enrute correctamente al caller.
 */
class DefaultDialerService {
  /**
   * Verifica si esta app es el marcador predeterminado
   */
  async isDefaultDialer(): Promise<boolean> {
    try {
      const isDefault = await DefaultDialerModule.isDefaultDialer();
      console.log('🔍 ¿Es marcador predeterminado?', isDefault);
      return isDefault;
    } catch (error) {
      console.error('❌ Error verificando marcador predeterminado:', error);
      return false;
    }
  }

  /**
   * Solicita al usuario establecer esta app como marcador predeterminado
   *
   * Esto abrirá un diálogo del sistema donde el usuario debe seleccionar tu app.
   * Una vez hecho, InCallService funcionará correctamente y el audio IVR
   * se enrutará al caller (no al receptor).
   */
  async requestSetDefaultDialer(): Promise<boolean> {
    try {
      console.log('📱 Solicitando ser marcador predeterminado...');
      await DefaultDialerModule.requestSetDefaultDialer();
      return true;
    } catch (error) {
      console.error('❌ Error solicitando marcador predeterminado:', error);
      return false;
    }
  }

  /**
   * Obtiene el nombre del paquete del marcador predeterminado actual
   */
  async getCurrentDefaultDialer(): Promise<string> {
    try {
      const currentDialer = await DefaultDialerModule.getCurrentDefaultDialer();
      console.log('🔍 Marcador predeterminado actual:', currentDialer);
      return currentDialer;
    } catch (error) {
      console.error('❌ Error obteniendo marcador predeterminado:', error);
      return '';
    }
  }
}

export default new DefaultDialerService();
