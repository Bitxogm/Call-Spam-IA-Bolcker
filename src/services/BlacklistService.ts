// src/services/BlacklistService.ts
import { NativeModules } from 'react-native';

const { BlacklistModule } = NativeModules;

/**
 * Servicio para gestionar la lista negra usando SharedPreferences nativo
 * Mucho más simple y confiable que SQLite para este caso
 */
class BlacklistService {

  /**
   * Añade un número a la lista negra
   */
  addNumber = async (number: string): Promise<boolean> => {
    try {
      await BlacklistModule.addNumber(number);
      console.log(`✅ Número añadido a blacklist: ${number}`);
      return true;
    } catch (error) {
      console.log('❌ Error añadiendo número:', error);
      return false;
    }
  };

  /**
   * Elimina un número de la lista negra
   */
  removeNumber = async (number: string): Promise<boolean> => {
    try {
      await BlacklistModule.removeNumber(number);
      console.log(`🗑️ Número eliminado de blacklist: ${number}`);
      return true;
    } catch (error) {
      console.log('❌ Error eliminando número:', error);
      return false;
    }
  };

  /**
   * Verifica si un número está en la lista negra
   */
  isInBlacklist = async (number: string): Promise<boolean> => {
    try {
      const isSpam = await BlacklistModule.isInBlacklist(number);
      console.log(`🔍 ¿${number} en blacklist? ${isSpam ? 'SÍ' : 'NO'}`);
      return isSpam;
    } catch (error) {
      console.log('❌ Error verificando blacklist:', error);
      return false;
    }
  };

  /**
   * Obtiene todos los números de la lista negra
   */
  getAllNumbers = async (): Promise<string[]> => {
    try {
      const numbers = await BlacklistModule.getAllNumbers();
      console.log(`📋 Obtenidos ${numbers.length} números de blacklist`);
      return numbers;
    } catch (error) {
      console.log('❌ Error obteniendo números:', error);
      return [];
    }
  };

  /**
   * Obtiene el número de elementos en la blacklist
   */
  getCount = async (): Promise<number> => {
    try {
      const count = await BlacklistModule.getCount();
      return count;
    } catch (error) {
      console.log('❌ Error obteniendo count:', error);
      return 0;
    }
  };

  /**
   * Limpia toda la lista negra
   */
  clearAll = async (): Promise<boolean> => {
    try {
      await BlacklistModule.clearAll();
      console.log('🗑️ Blacklist limpiada completamente');
      return true;
    } catch (error) {
      console.log('❌ Error limpiando blacklist:', error);
      return false;
    }
  };
}

// Exportar instancia singleton
export const blacklistService = new BlacklistService();
