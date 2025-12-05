// src/services/DatabaseService.ts
import * as SQLite from 'expo-sqlite';

// Definir tipos TypeScript para nuestros datos
export interface SpamNumber {
  id: number;
  number: string;
  reason: string;
  source: 'manual' | 'auto' | 'reported';
  date_added: string;
  is_active: boolean;
}

export interface CallHistory {
  id: number;
  phone_number: string;
  call_date: string;
  was_blocked: boolean;
  block_reason: string;
  ai_conversation_id?: string;
}

export interface AppSettings {
  id: number;
  setting_key: string;
  setting_value: string;
}

// Clase para manejar toda la base de datos
class DatabaseService {
  private db: SQLite.SQLiteDatabase | null = null;

  constructor() {
    this.initDatabase();
  }

  // Función para normalizar números de teléfono
  private normalizePhoneNumber(number: string): string {
    // Eliminar todo excepto dígitos
    let cleaned = number.replace(/\D/g, '');

    // Si empieza con código de país, obtener últimos 9 dígitos (España)
    // Esto maneja: +34612345678 → 612345678
    if (cleaned.length > 9) {
      cleaned = cleaned.slice(-9);
    }

    console.log(`📞 Normalizado: "${number}" → "${cleaned}"`);
    return cleaned;
  }

  // Inicializar base de datos (versión moderna de Expo SQLite)
  private initDatabase = async () => {
    try {
      // Abrir base de datos con la nueva API
      this.db = await SQLite.openDatabaseAsync('spamBlocker.db');
      
      // Crear tablas
      await this.createTables();
      
      console.log('✅ Base de datos inicializada correctamente');
    } catch (error) {
      console.log('❌ Error inicializando base de datos:', error);
    }
  };

  private createTables = async () => {
    if (!this.db) return;

    try {
      // Tabla: números spam
      await this.db.execAsync(`
        CREATE TABLE IF NOT EXISTS spam_numbers (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          number TEXT UNIQUE NOT NULL,
          reason TEXT,
          source TEXT NOT NULL,
          date_added TEXT NOT NULL,
          is_active INTEGER DEFAULT 1
        );
      `);

      // Tabla: historial de llamadas
      await this.db.execAsync(`
        CREATE TABLE IF NOT EXISTS call_history (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          phone_number TEXT NOT NULL,
          call_date TEXT NOT NULL,
          was_blocked INTEGER NOT NULL,
          block_reason TEXT,
          ai_conversation_id TEXT
        );
      `);

      // Tabla: configuraciones de la app
      await this.db.execAsync(`
        CREATE TABLE IF NOT EXISTS app_settings (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          setting_key TEXT UNIQUE NOT NULL,
          setting_value TEXT NOT NULL
        );
      `);

      // Insertar configuraciones por defecto
      await this.db.runAsync(
        `INSERT OR IGNORE INTO app_settings (setting_key, setting_value) 
         VALUES (?, ?);`,
        ['radical_mode', 'false']
      );

      await this.db.runAsync(
        `INSERT OR IGNORE INTO app_settings (setting_key, setting_value) 
         VALUES (?, ?);`,
        ['total_blocked_calls', '0']
      );

    } catch (error) {
      console.log('❌ Error creando tablas:', error);
    }
  };

  // MÉTODOS PARA NÚMEROS SPAM

  // Añadir número spam
  addSpamNumber = async (number: string, reason: string, source: 'manual' | 'auto' | 'reported'): Promise<boolean> => {
    if (!this.db) return false;

    try {
      // NORMALIZAR antes de guardar
      const normalizedNumber = this.normalizePhoneNumber(number);

      await this.db.runAsync(
        `INSERT INTO spam_numbers (number, reason, source, date_added)
         VALUES (?, ?, ?, ?);`,
        [normalizedNumber, reason, source, new Date().toISOString().split('T')[0]]
      );

      console.log(`✅ Número "${number}" normalizado a "${normalizedNumber}" y añadido a la base de datos`);
      return true;
    } catch (error) {
      console.log('❌ Error añadiendo número:', error);
      return false;
    }
  };

  // Obtener todos los números spam
  getSpamNumbers = async (): Promise<SpamNumber[]> => {
    if (!this.db) return [];

    try {
      const rows = await this.db.getAllAsync(
        `SELECT * FROM spam_numbers WHERE is_active = 1 ORDER BY date_added DESC;`
      );
      
      console.log(`📋 Obtenidos ${rows.length} números spam`);
      return rows as SpamNumber[];
    } catch (error) {
      console.log('❌ Error obteniendo números:', error);
      return [];
    }
  };

  // Eliminar número spam (marcar como inactivo)
  deleteSpamNumber = async (id: number): Promise<boolean> => {
    if (!this.db) return false;

    try {
      await this.db.runAsync(
        `UPDATE spam_numbers SET is_active = 0 WHERE id = ?;`,
        [id]
      );
      
      console.log(`🗑️ Número ID ${id} eliminado`);
      return true;
    } catch (error) {
      console.log('❌ Error eliminando número:', error);
      return false;
    }
  };

  // Verificar si un número es spam
  isSpamNumber = async (number: string): Promise<boolean> => {
    if (!this.db) return false;

    try {
      // NORMALIZAR antes de comparar
      const normalizedNumber = this.normalizePhoneNumber(number);

      const result = await this.db.getFirstAsync(
        `SELECT COUNT(*) as count FROM spam_numbers
         WHERE number = ? AND is_active = 1;`,
        [normalizedNumber]
      ) as { count: number } | null;

      const isSpam = result ? result.count > 0 : false;
      console.log(`🔍 ¿"${number}" (normalizado: "${normalizedNumber}") es spam? ${isSpam ? '✅ SÍ' : '❌ NO'}`);

      return isSpam;
    } catch (error) {
      console.log('❌ Error verificando número spam:', error);
      return false;
    }
  };

  // MÉTODOS PARA HISTORIAL DE LLAMADAS

  // Registrar llamada bloqueada
  logBlockedCall = async (phoneNumber: string, reason: string): Promise<boolean> => {
    if (!this.db) return false;

    try {
      await this.db.runAsync(
        `INSERT INTO call_history (phone_number, call_date, was_blocked, block_reason) 
         VALUES (?, ?, 1, ?);`,
        [phoneNumber, new Date().toISOString(), reason]
      );
      
      console.log(`📱 Llamada bloqueada registrada: ${phoneNumber}`);
      return true;
    } catch (error) {
      console.log('❌ Error registrando llamada:', error);
      return false;
    }
  };

  // Obtener historial de llamadas
  getCallHistory = async (limit: number = 50): Promise<CallHistory[]> => {
    if (!this.db) return [];

    try {
      const rows = await this.db.getAllAsync(
        `SELECT * FROM call_history 
         ORDER BY call_date DESC LIMIT ?;`,
        [limit]
      );
      
      return rows as CallHistory[];
    } catch (error) {
      console.log('❌ Error obteniendo historial:', error);
      return [];
    }
  };

  // MÉTODOS PARA CONFIGURACIONES

  // Obtener configuración
  getSetting = async (key: string): Promise<string | null> => {
    if (!this.db) return null;

    try {
      const result = await this.db.getFirstAsync(
        `SELECT setting_value FROM app_settings WHERE setting_key = ?;`,
        [key]
      ) as { setting_value: string } | null;

      return result ? result.setting_value : null;
    } catch (error) {
      console.log('❌ Error obteniendo configuración:', error);
      return null;
    }
  };

  // Guardar configuración
  setSetting = async (key: string, value: string): Promise<boolean> => {
    if (!this.db) return false;

    try {
      await this.db.runAsync(
        `INSERT OR REPLACE INTO app_settings (setting_key, setting_value) 
         VALUES (?, ?);`,
        [key, value]
      );
      
      console.log(`⚙️ Configuración guardada: ${key} = ${value}`);
      return true;
    } catch (error) {
      console.log('❌ Error guardando configuración:', error);
      return false;
    }
  };

  // Incrementar contador de llamadas bloqueadas
  incrementBlockedCalls = async (): Promise<number> => {
    try {
      const currentValue = await this.getSetting('total_blocked_calls');
      const newValue = (parseInt(currentValue || '0') + 1).toString();
      await this.setSetting('total_blocked_calls', newValue);
      return parseInt(newValue);
    } catch (error) {
      console.log('❌ Error incrementando contador:', error);
      return 0;
    }
  };

  // Método para verificar si la base de datos está lista
  isReady = (): boolean => {
    return this.db !== null;
  };
}

// Crear instancia única (singleton)
export const databaseService = new DatabaseService();