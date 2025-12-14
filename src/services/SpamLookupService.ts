// SpamLookupService.ts
import { NativeModules, Alert } from 'react-native';

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
   * Abre Truecaller para consultar número
   */
  openTruecaller = async (phoneNumber: string): Promise<boolean> => {
    try {
      await SpamLookupModule.openTruecaller(phoneNumber);
      console.log(`🔍 Abriendo Truecaller para: ${phoneNumber}`);
      return true;
    } catch (error) {
      console.error('❌ Error abriendo Truecaller:', error);
      Alert.alert('Error', 'No se pudo abrir Truecaller');
      return false;
    }
  };

  /**
   * Abre ResponderONo.es para consultar número
   */
  openResponderONo = async (phoneNumber: string): Promise<boolean> => {
    try {
      await SpamLookupModule.openResponderONo(phoneNumber);
      console.log(`🔍 Abriendo ResponderONo para: ${phoneNumber}`);
      return true;
    } catch (error) {
      console.error('❌ Error abriendo ResponderONo:', error);
      Alert.alert('Error', 'No se pudo abrir ResponderONo');
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
   * Muestra opciones de consulta
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
      ]
    );
  };
}

export default new SpamLookupService();
