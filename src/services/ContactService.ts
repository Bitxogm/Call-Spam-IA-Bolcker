// src/services/ContactsService.ts - Versión corregida
import * as Contacts from 'expo-contacts';

export interface Contact {
  id: string;
  name: string;
  phoneNumbers: string[];
}

class ContactsService {
  private contacts: Contact[] = [];
  private contactsLoaded = false;

  // Verificar permisos
  checkPermissions = async (): Promise<boolean> => {
    try {
      const { status } = await Contacts.getPermissionsAsync();
      console.log('📞 Estado permisos:', status);
      return status === 'granted';
    } catch (error) {
      console.log('❌ Error verificando permisos:', error);
      return false;
    }
  };

  // Solicitar permisos
  requestPermissions = async () => {
    try {
      console.log('📞 Solicitando permisos...');
      const { status } = await Contacts.requestPermissionsAsync();
      return { granted: status === 'granted', canAskAgain: true };
    } catch (error) {
      console.log('❌ Error solicitando permisos:', error);
      return { granted: false, canAskAgain: false };
    }
  };

  // Cargar contactos y GUARDARLOS
  loadContacts = async (): Promise<Contact[]> => {
    try {
      const hasPermission = await this.checkPermissions();
      if (!hasPermission) return [];

      const { data } = await Contacts.getContactsAsync({
        fields: [Contacts.Fields.Name, Contacts.Fields.PhoneNumbers],
      });

      // Filtrar contactos que tengan números
      const formattedContacts: Contact[] = data
        .filter(contact => contact.phoneNumbers && contact.phoneNumbers.length > 0)
        .map(contact => ({
          id: contact.id || 'unknown',
          name: contact.name || 'Sin nombre',
          phoneNumbers: contact.phoneNumbers!.map(phone => phone.number || '')
        }));

      // GUARDAR los contactos
      this.contacts = formattedContacts;
      this.contactsLoaded = true;

      console.log(`📞 Contactos cargados y guardados: ${formattedContacts.length}`);
      return formattedContacts;
    } catch (error) {
      console.log('❌ Error cargando contactos:', error);
      return [];
    }
  };

  // Estadísticas REALES
  getContactsStats = () => {
    const totalContacts = this.contacts.length;
    const totalPhoneNumbers = this.contacts.reduce((total, contact) => 
      total + contact.phoneNumbers.length, 0
    );

    console.log(`📊 Stats contactos: ${totalContacts} contactos, ${totalPhoneNumbers} números`);

    return {
      totalContacts,
      totalPhoneNumbers,
      isLoaded: this.contactsLoaded
    };
  };

  // Obtener contactos cargados
  getLoadedContacts = (): Contact[] => {
    return this.contacts;
  };

  // Verificar si número está en contactos
  isNumberInContacts = async (phoneNumber: string): Promise<boolean> => {
    if (!this.contactsLoaded) {
      await this.loadContacts();
    }

    const cleanNumber = phoneNumber.replace(/[^\\d]/g, '');
    
    const found = this.contacts.some(contact =>
      contact.phoneNumbers.some(contactPhone => {
        const cleanContactPhone = contactPhone.replace(/[^\\d]/g, '');
        return cleanContactPhone.includes(cleanNumber) || cleanNumber.includes(cleanContactPhone);
      })
    );

    console.log(`📞 Número ${phoneNumber} ${found ? 'ENCONTRADO' : 'NO ENCONTRADO'} en contactos`);
    return found;
  };

  // Método para modo radical
  shouldBlockInRadicalMode = async (phoneNumber: string) => {
    // Números de emergencia nunca se bloquean
    const emergencyNumbers = ['112', '091', '092', '080', '061'];
    const isEmergency = emergencyNumbers.some(emergency => phoneNumber.includes(emergency));
    
    if (isEmergency) {
      return {
        shouldBlock: false,
        reason: 'Número de emergencia'
      };
    }

    // Verificar si está en contactos
    const isInContacts = await this.isNumberInContacts(phoneNumber);
    
    if (isInContacts) {
      return {
        shouldBlock: false,
        reason: 'Número en contactos'
      };
    }

    return {
      shouldBlock: true,
      reason: 'Número no autorizado'
    };
  };
}

// Exportar instancia
export const contactsService = new ContactsService();