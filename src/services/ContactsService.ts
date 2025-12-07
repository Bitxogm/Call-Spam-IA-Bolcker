// ContactsService.ts
import { NativeModules } from 'react-native';

const { ContactsModule } = NativeModules;

export interface ContactInfo {
  id: number;
  name: string;
  number: string;
  photoUri: string | null;
}

/**
 * Servicio para gestionar whitelist de contactos y Modo Radical
 */
class ContactsService {
  /**
   * Verifica si un número está en los contactos
   */
  isInContacts = async (number: string): Promise<boolean> => {
    try {
      const isContact = await ContactsModule.isInContacts(number);
      console.log(`📞 ${number} ${isContact ? 'ES' : 'NO ES'} contacto`);
      return isContact;
    } catch (error) {
      console.error('❌ Error verificando contacto:', error);
      return false;
    }
  };

  /**
   * Obtiene información completa de un contacto
   */
  getContactInfo = async (number: string): Promise<ContactInfo | null> => {
    try {
      const contact = await ContactsModule.getContactInfo(number);
      if (contact) {
        console.log(`👤 Contacto encontrado: ${contact.name}`);
      }
      return contact;
    } catch (error) {
      console.error('❌ Error obteniendo info de contacto:', error);
      return null;
    }
  };

  /**
   * Activa o desactiva el Modo Radical
   * Modo Radical = Solo permitir llamadas de contactos, bloquear todo lo demás
   */
  setModoRadical = async (enabled: boolean): Promise<boolean> => {
    try {
      await ContactsModule.setModoRadical(enabled);
      console.log(`🚫 Modo Radical ${enabled ? 'ACTIVADO' : 'DESACTIVADO'}`);
      return true;
    } catch (error) {
      console.error('❌ Error configurando Modo Radical:', error);
      return false;
    }
  };

  /**
   * Lee el estado actual del Modo Radical
   */
  isModoRadicalEnabled = async (): Promise<boolean> => {
    try {
      const enabled = await ContactsModule.isModoRadicalEnabled();
      return enabled;
    } catch (error) {
      console.error('❌ Error leyendo Modo Radical:', error);
      return false;
    }
  };

  /**
   * Cuenta total de contactos (para estadísticas)
   */
  getContactsCount = async (): Promise<number> => {
    try {
      const count = await ContactsModule.getContactsCount();
      return count;
    } catch (error) {
      console.error('❌ Error contando contactos:', error);
      return -1;
    }
  };
}

export default new ContactsService();
