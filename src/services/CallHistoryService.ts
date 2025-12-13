// CallHistoryService.ts
import { NativeModules } from 'react-native';

const { CallHistoryModule } = NativeModules;

export interface SpamCallRecord {
  timestamp: string;
  number: string;
  reason: string;      // "Blacklist", "Modo Radical", "Premium", etc.
  action: string;      // "Answer+Hangup", "Notification", "Blocked"
  spamScore: number;   // 0-100
  category: string;    // "BLACKLIST", "TELEMARKETING_LEGAL", "PREMIUM", etc.
}

class CallHistoryService {
  /**
   * Obtiene el historial de llamadas spam
   */
  getHistory = async (): Promise<SpamCallRecord[]> => {
    try {
      const history = await CallHistoryModule.getHistory();
      console.log(`📞 Historial obtenido: ${history.length} registros`);
      return history;
    } catch (error) {
      console.error('❌ Error obteniendo historial:', error);
      return [];
    }
  };

  /**
   * Obtiene el total de llamadas spam
   */
  getTotalSpamCalls = async (): Promise<number> => {
    try {
      const total = await CallHistoryModule.getTotalSpamCalls();
      return total;
    } catch (error) {
      console.error('❌ Error obteniendo total:', error);
      return 0;
    }
  };

  /**
   * Limpia el historial de llamadas
   */
  clearHistory = async (): Promise<boolean> => {
    try {
      await CallHistoryModule.clearHistory();
      console.log('🗑️ Historial cleared');
      return true;
    } catch (error) {
      console.error('❌ Error limpiando historial:', error);
      return false;
    }
  };
}

export default new CallHistoryService();
