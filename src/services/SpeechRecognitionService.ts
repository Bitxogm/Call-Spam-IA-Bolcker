// src/services/SpeechRecognitionService.ts
import { NativeModules, NativeEventEmitter, EmitterSubscription, Platform } from 'react-native';

const { SpeechRecognitionModule } = NativeModules;

export type SpeechRecognitionEvent =
  | 'onSpeechStart'
  | 'onSpeechRecognized'
  | 'onSpeechEnd'
  | 'onSpeechError'
  | 'onSpeechResults'
  | 'onSpeechPartialResults'
  | 'onSpeechVolumeChanged';

export interface SpeechRecognitionResult {
  value: string;
  confidence?: number;
}

export interface SpeechRecognitionError {
  code: number;
  message: string;
}

/**
 * Servicio para reconocimiento de voz (Speech-to-Text)
 * Usa Google Speech Recognition en Android
 */
class SpeechRecognitionService {
  private eventEmitter: NativeEventEmitter | null = null;
  private subscriptions: Map<string, EmitterSubscription> = new Map();
  private isInitialized = false;

  constructor() {
    if (Platform.OS === 'android' && SpeechRecognitionModule) {
      this.eventEmitter = new NativeEventEmitter(SpeechRecognitionModule);
      this.isInitialized = true;
    }
  }

  /**
   * Verifica si Speech Recognition está disponible
   */
  async isAvailable(): Promise<boolean> {
    if (!this.isInitialized) {
      console.warn('⚠️ SpeechRecognition no está disponible en esta plataforma');
      return false;
    }

    try {
      const available = await SpeechRecognitionModule.isAvailable();
      return available;
    } catch (error) {
      console.error('❌ Error verificando disponibilidad:', error);
      return false;
    }
  }

  /**
   * Inicia el reconocimiento de voz
   * @param language Código de idioma (por defecto: 'es-ES')
   */
  async startListening(language: string = 'es-ES'): Promise<boolean> {
    if (!this.isInitialized) {
      throw new Error('SpeechRecognition no está disponible');
    }

    try {
      await SpeechRecognitionModule.startListening(language);
      console.log('🎤 Reconocimiento de voz iniciado');
      return true;
    } catch (error) {
      console.error('❌ Error iniciando reconocimiento:', error);
      throw error;
    }
  }

  /**
   * Detiene el reconocimiento de voz
   */
  async stopListening(): Promise<boolean> {
    if (!this.isInitialized) {
      return false;
    }

    try {
      await SpeechRecognitionModule.stopListening();
      console.log('🛑 Reconocimiento de voz detenido');
      return true;
    } catch (error) {
      console.error('❌ Error deteniendo reconocimiento:', error);
      return false;
    }
  }

  /**
   * Cancela el reconocimiento de voz
   */
  async cancel(): Promise<boolean> {
    if (!this.isInitialized) {
      return false;
    }

    try {
      await SpeechRecognitionModule.cancel();
      console.log('❌ Reconocimiento cancelado');
      return true;
    } catch (error) {
      console.error('❌ Error cancelando reconocimiento:', error);
      return false;
    }
  }

  /**
   * Destruye el reconocedor (limpieza)
   */
  async destroy(): Promise<void> {
    if (!this.isInitialized) {
      return;
    }

    try {
      // Remover todos los listeners
      this.removeAllListeners();

      // Destruir el reconocedor nativo
      await SpeechRecognitionModule.destroy();
      console.log('♻️ SpeechRecognition destruido');
    } catch (error) {
      console.error('❌ Error destruyendo reconocedor:', error);
    }
  }

  /**
   * Suscribirse a eventos de reconocimiento
   */
  addEventListener(
    event: SpeechRecognitionEvent,
    callback: (data?: any) => void
  ): EmitterSubscription | null {
    if (!this.eventEmitter) {
      console.warn('⚠️ EventEmitter no disponible');
      return null;
    }

    const subscription = this.eventEmitter.addListener(event, callback);
    this.subscriptions.set(event, subscription);
    return subscription;
  }

  /**
   * Cancelar suscripción a evento
   */
  removeEventListener(event: SpeechRecognitionEvent): void {
    const subscription = this.subscriptions.get(event);
    if (subscription) {
      subscription.remove();
      this.subscriptions.delete(event);
    }
  }

  /**
   * Cancelar todas las suscripciones
   */
  removeAllListeners(): void {
    this.subscriptions.forEach((subscription) => subscription.remove());
    this.subscriptions.clear();
  }

  /**
   * Helper: Reconocer voz con Promise (más fácil de usar)
   * Devuelve el texto reconocido o lanza error
   */
  async recognize(language: string = 'es-ES'): Promise<string> {
    return new Promise(async (resolve, reject) => {
      if (!this.isInitialized) {
        reject(new Error('SpeechRecognition no disponible'));
        return;
      }

      let timeoutId: NodeJS.Timeout | null = null;

      // Listener para resultados
      const resultsListener = this.addEventListener('onSpeechResults', (data: SpeechRecognitionResult) => {
        if (timeoutId) clearTimeout(timeoutId);
        this.cleanup();
        resolve(data.value);
      });

      // Listener para errores
      const errorListener = this.addEventListener('onSpeechError', (data: SpeechRecognitionError) => {
        if (timeoutId) clearTimeout(timeoutId);
        this.cleanup();

        // Si el error es "no se reconoció voz", devolver string vacío en lugar de error
        if (data.code === 7) {  // ERROR_NO_MATCH
          resolve('');
        } else {
          reject(new Error(data.message));
        }
      });

      // Timeout de seguridad (10 segundos)
      timeoutId = setTimeout(() => {
        this.cancel();
        this.cleanup();
        reject(new Error('Timeout: No se detectó voz en 10 segundos'));
      }, 10000);

      // Función para limpiar listeners
      const cleanup = () => {
        if (resultsListener) resultsListener.remove();
        if (errorListener) errorListener.remove();
      };

      this.cleanup = cleanup;

      // Iniciar reconocimiento
      try {
        await this.startListening(language);
      } catch (error) {
        if (timeoutId) clearTimeout(timeoutId);
        cleanup();
        reject(error);
      }
    });
  }

  private cleanup: () => void = () => {};
}

export const speechRecognitionService = new SpeechRecognitionService();
export default speechRecognitionService;
