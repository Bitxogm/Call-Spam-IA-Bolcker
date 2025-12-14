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
   * Muestra primer grupo de opciones (Android limita Alert a 3 botones)
   */
  private showFirstGroup = (phoneNumber: string) => {
    Alert.alert(
      '🔍 Consultar Número (1/2)',
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
          text: 'Más opciones →',
          onPress: () => this.showSecondGroup(phoneNumber),
        },
      ]
    );
  };

  /**
   * Muestra segundo grupo de opciones
   */
  private showSecondGroup = (phoneNumber: string) => {
    Alert.alert(
      '🔍 Consultar Número (2/2)',
      `¿Dónde quieres consultar ${phoneNumber}?`,
      [
        {
          text: 'Truecaller',
          onPress: () => this.openTruecaller(phoneNumber),
        },
        {
          text: 'ResponderONo.es',
          onPress: () => this.openResponderONo(phoneNumber),
        },
        {
          text: '← Volver',
          onPress: () => this.showFirstGroup(phoneNumber),
        },
      ]
    );
  };

  /**
   * Muestra opciones de consulta (dividido en 2 pantallas por límite de Android)
   */
  showLookupOptions = (phoneNumber: string) => {
    this.showFirstGroup(phoneNumber);
  };
}

export default new SpamLookupService();
