// SpamLookupService.ts
import { NativeModules, Alert, ActionSheetIOS, Platform } from 'react-native';

const { SpamLookupModule } = NativeModules;

class SpamLookupService {
  /**
   * Abre ListaSpam.com para consultar número
   */
  openListaSpam = async (phoneNumber: string): Promise<boolean> => {
    try {
      await SpamLookupModule.openListaSpam(phoneNumber);
      console.log(`🔍 Abriendo ListaSpam para: ${phoneNumber}`);
      return true;
    } catch (error) {
      console.error('❌ Error abriendo ListaSpam:', error);
      Alert.alert('Error', 'No se pudo abrir ListaSpam');
      return false;
    }
  };

  /**
   * Abre CleverDialer.es para consultar número
   */
  openCleverDialer = async (phoneNumber: string): Promise<boolean> => {
    try {
      await SpamLookupModule.openCleverDialer(phoneNumber);
      console.log(`🔍 Abriendo CleverDialer para: ${phoneNumber}`);
      return true;
    } catch (error) {
      console.error('❌ Error abriendo CleverDialer:', error);
      Alert.alert('Error', 'No se pudo abrir CleverDialer');
      return false;
    }
  };

  /**
   * Muestra opciones de consulta (solo servicios que funcionan)
   */
  showLookupOptions = (phoneNumber: string) => {
    Alert.alert(
      '🔍 Consultar Número',
      `¿Dónde quieres consultar ${phoneNumber}?`,
      [
        {
          text: 'ListaSpam.com',
          onPress: () => this.openListaSpam(phoneNumber),
        },
        {
          text: 'CleverDialer.es',
          onPress: () => this.openCleverDialer(phoneNumber),
        },
        {
          text: 'Cancelar',
          style: 'cancel',
        },
      ],
      { cancelable: true } // Permite cerrar tocando fuera
    );
  };
}

export default new SpamLookupService();
