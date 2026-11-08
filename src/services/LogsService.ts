// LogsService.ts
import { NativeModules } from 'react-native';

const { LogsModule } = NativeModules;

export interface LogEntry {
  timestamp: string;
  level: 'INFO' | 'ERROR' | 'DEBUG' | 'WARNING';
  message: string;
}

class LogsService {
  /**
   * Añade un log de tipo INFO
   */
  logInfo = async (message: string): Promise<boolean> => {
    try {
      await LogsModule.logInfo(message);
      return true;
    } catch (error) {
      console.error('❌ Error logging INFO:', error);
      return false;
    }
  };

  /**
   * Añade un log de tipo ERROR
   */
  logError = async (message: string): Promise<boolean> => {
    try {
      await LogsModule.logError(message);
      return true;
    } catch (error) {
      console.error('❌ Error logging ERROR:', error);
      return false;
    }
  };

  /**
   * Añade un log de tipo DEBUG
   */
  logDebug = async (message: string): Promise<boolean> => {
    try {
      await LogsModule.logDebug(message);
      return true;
    } catch (error) {
      console.error('❌ Error logging DEBUG:', error);
      return false;
    }
  };

  /**
   * Añade un log de tipo WARNING
   */
  logWarning = async (message: string): Promise<boolean> => {
    try {
      await LogsModule.logWarning(message);
      return true;
    } catch (error) {
      console.error('❌ Error logging WARNING:', error);
      return false;
    }
  };

  /**
   * Obtiene todos los logs como string formateado
   */
  getLogsAsString = async (): Promise<string> => {
    try {
      const logs = await LogsModule.getLogsAsString();
      return logs;
    } catch (error) {
      console.error('❌ Error obteniendo logs:', error);
      return 'Error obteniendo logs: ' + error;
    }
  };

  /**
   * Obtiene todos los logs como array de objetos
   */
  /**
   * Como getLogs, pero devuelve el error en vez de esconderlo tras una lista vacía:
   * "No hay logs" y "no se pueden leer" no pueden verse igual en pantalla.
   */
  getLogsDetallado = async (): Promise<{ logs: LogEntry[]; error: string | null }> => {
    try {
      if (!LogsModule) {
        return { logs: [], error: 'el módulo nativo LogsModule no está disponible' };
      }
      const logs = await LogsModule.getLogs();
      if (!Array.isArray(logs)) {
        return { logs: [], error: 'LogsModule.getLogs no devolvió una lista' };
      }
      return { logs, error: null };
    } catch (error) {
      return { logs: [], error: error instanceof Error ? error.message : String(error) };
    }
  };

  getLogs = async (): Promise<LogEntry[]> => {
    try {
      const logs = await LogsModule.getLogs();
      return logs;
    } catch (error) {
      console.error('❌ Error obteniendo logs:', error);
      return [];
    }
  };

  /**
   * Limpia todos los logs
   */
  clearLogs = async (): Promise<boolean> => {
    try {
      await LogsModule.clearLogs();
      console.log('🗑️ Logs cleared');
      return true;
    } catch (error) {
      console.error('❌ Error limpiando logs:', error);
      return false;
    }
  };
}

export default new LogsService();
