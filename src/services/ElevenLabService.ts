// src/services/ElevenLabsService.ts
import { Buffer } from 'buffer';
import { Audio } from 'expo-av';
import axios from 'axios';
import * as Speech from 'expo-speech';

// Tipos TypeScript
export interface VoiceConfig {
  voice_id: string;
  name: string;
  description: string;
  gender: 'male' | 'female';
  age: 'young' | 'middle_aged' | 'old';
  accent: string;
}

export interface TTSResponse {
  success: boolean;
  audioUri?: string;
  error?: string;
  charactersUsed?: number;
}

// Voces preconfiguradas para nuestras personalidades
// REEMPLAZAR la sección VOICE_PROFILES por esta:
export const VOICE_PROFILES: Record<string, VoiceConfig> = {
  manolo: {
    voice_id: 'onwK4e9ZLuTAKqWW03F9', // Daniel (español España, masculino mayor)
    name: 'Manolo (Abuelo)',
    description: 'Voz masculina mayor española',
    gender: 'male',
    age: 'old',
    accent: 'spanish_spain'
  },
  paquita: {
    voice_id: 'AZnzlk1XvdvUeBnXmlld', // Domi (español España, femenina dulce)
    name: 'Paquita (Ama de Casa)',
    description: 'Voz femenina mayor española',
    gender: 'female',
    age: 'middle_aged',
    accent: 'spanish_spain'
  },
  roberto: {
    voice_id: '5IDdqnXnlsZ1FCxoOFYg', // ← JAVIER BARDEM
    name: 'Roberto (Indeciso - Javier Bardem)',
    description: 'Voz de Javier Bardem para el oficinista indeciso',
    gender: 'male',
    age: 'middle_aged',
    accent: 'spanish_spain'
  },



};

class ElevenLabsService {
  private readonly apiKey: string;
  private readonly baseUrl = 'https://api.elevenlabs.io/v1';

  constructor() {
    this.apiKey = process.env.EXPO_PUBLIC_ELEVENLABS_API_KEY || '';
    console.log('🗣️ ElevenLabsService inicializado');
  }

  // Verificar configuración
  isConfigured = (): boolean => {
    if (!this.apiKey) {
      console.log('❌ EXPO_PUBLIC_ELEVENLABS_API_KEY no configurada');
      return false;
    }
    return true;
  };

  // Convertir texto a audio usando ElevenLabs
  textToSpeech = async (
    text: string,
    personality: 'manolo' | 'paquita' | 'roberto' = 'manolo'
  ): Promise<TTSResponse> => {
    try {
      if (!this.isConfigured()) {
        return {
          success: false,
          error: 'API Key de ElevenLabs no configurada'
        };
      }

      const voiceConfig = VOICE_PROFILES[personality];

      console.log(`🗣️ Generando audio para ${voiceConfig.name}: "${text.substring(0, 50)}..."`);
      console.log('🔍 DEBUG - API Key being sent:', this.apiKey);
      console.log('🔍 DEBUG - API Key length:', this.apiKey.length);
      console.log('🔍 DEBUG - Headers being sent:', {
        'Accept': 'audio/mpeg',
        'Content-Type': 'application/json',
        'xi-api-key': this.apiKey
      });

      // Configuración optimizada para español
      const requestBody = {
        text: text,
        model_id: "eleven_multilingual_v2", // Mejor modelo para español
        voice_settings: {
          stability: 0.3,        // Balance entre consistencia y expresividad
          similarity_boost: 0.8, // Mantener características de la voz
          style: 0.2,            // Neutral (no available en todos los modelos)
          use_speaker_boost: true, // Mejorar calidad de audio
          speaking_rate: 0.7
        }
      };


      const response = await axios.post(
        `${this.baseUrl}/text-to-speech/${voiceConfig.voice_id}`,
        requestBody,
        {
          headers: {
            'Accept': 'audio/mpeg',
            'Content-Type': 'application/json',
            'xi-api-key': this.apiKey
          },
          responseType: 'arraybuffer', // Importante para audio
          timeout: 30000 // 30 segundos timeout
        }
      );

      // Convertir arraybuffer a base64
      const audioData = response.data;
      const base64Audio = Buffer.from(audioData).toString('base64');
      const audioUri = `data:audio/mpeg;base64,${base64Audio}`;

      console.log(`✅ Audio generado exitosamente para ${voiceConfig.name}`);

      // Obtener información de caracteres usados del header
      const charactersUsed = parseInt(response.headers['xi-characters-used'] || '0');

      return {
        success: true,
        audioUri: audioUri,
        charactersUsed: charactersUsed
      };

    } catch (error: any) {
      console.log('❌ Error en ElevenLabs TTS:', error.message);

      // Manejo de errores específicos
      if (error.response?.status === 401) {
        return {
          success: false,
          error: 'API Key inválida de ElevenLabs'
        };
      } else if (error.response?.status === 402) {
        return {
          success: false,
          error: 'Cuota de caracteres agotada en ElevenLabs'
        };
      } else if (error.response?.status === 422) {
        return {
          success: false,
          error: 'Texto demasiado largo o contiene caracteres inválidos'
        };
      }

      return {
        success: false,
        error: `Error TTS: ${error.message}`
      };
    }
  };

  // Reproducir audio
  playAudio = async (audioUri: string): Promise<boolean> => {
    try {
      console.log('🔊 Reproduciendo audio...');

      // Configurar modo de audio
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: false,
        playsInSilentModeIOS: true,
        shouldDuckAndroid: true,
        playThroughEarpieceAndroid: false,
      });

      // Crear y reproducir sonido
      const { sound } = await Audio.Sound.createAsync(
        { uri: audioUri },
        { shouldPlay: true, volume: 1.0 }
      );

      // Listener para cuando termine la reproducción
      sound.setOnPlaybackStatusUpdate((status) => {
        if (status.isLoaded && status.didJustFinish) {
          console.log('🔊 Reproducción terminada');
          sound.unloadAsync(); // Liberar memoria
        }
      });

      return true;
    } catch (error) {
      console.log('❌ Error reproduciendo audio:', error);
      return false;
    }
  };

  // Método combinado: TTS + Play
  speakText = async (
    text: string,
    personality: 'manolo' | 'paquita' | 'roberto' = 'manolo'
  ): Promise<boolean> => {
    //   try {
    //     // Generar audio
    //     const ttsResult = await this.textToSpeech(text, personality);

    //     if (!ttsResult.success || !ttsResult.audioUri) {
    //       console.log('❌ No se pudo generar audio:', ttsResult.error);
    //       return false;
    //     }

    //     // Reproducir audio
    //     const playResult = await this.playAudio(ttsResult.audioUri);

    //     if (ttsResult.charactersUsed) {
    //       console.log(`📊 Caracteres usados: ${ttsResult.charactersUsed}`);
    //     }

    //     return playResult;
    //   } catch (error) {
    //     console.log('❌ Error en speakText:', error);
    //     return false;
    //   }
    // };

    
    try {
      // Intentar ElevenLabs primero
      const ttsResult = await this.textToSpeech(text, personality);

      if (!ttsResult.success || !ttsResult.audioUri) {
        console.log('❌ ElevenLabs falló, usando TTS nativo');
        // Fallback a TTS nativo
        return this.speakWithNativeTTS(text, personality);
      }

      // ElevenLabs funcionó
      const playResult = await this.playAudio(ttsResult.audioUri);
      return playResult;
    } catch (error) {
      console.log('❌ Error en speakText, usando TTS nativo');
      return this.speakWithNativeTTS(text, personality);
    }
  };

  // Nueva función para TTS nativo
  private speakWithNativeTTS = async (
    text: string,
    personality: 'manolo' | 'paquita' | 'roberto'
  ): Promise<boolean> => {
    try {
      const voiceOptions = {
        rate: 0.8,        // Velocidad más lenta para personas mayores
        pitch: personality === 'paquita' ? 1.2 : 0.9,  // Paquita más aguda
        language: 'es-ES'
      };

      await Speech.speak(text, voiceOptions);
      return true;
    } catch (error) {
      console.log('❌ Error con TTS nativo:', error);
      return false;
    }
  };

  // Obtener información de cuota
  getQuotaInfo = async (): Promise<{ characters_used: number, characters_limit: number } | null> => {
    try {
      const response = await axios.get(`${this.baseUrl}/user`, {
        headers: {
          'xi-api-key': this.apiKey
        }
      });

      return {
        characters_used: response.data.character_count || 0,
        characters_limit: response.data.character_limit || 10000
      };
    } catch (error) {
      console.log('❌ Error obteniendo cuota:', error);
      return null;
    }
  };

  /**
   * Genera audio IVR corporativo y lo guarda en el almacenamiento nativo
   * Para ser usado en Modo 2 (Answer+Hangup con IVR)
   */
  generateAndSaveIVRAudio = async (): Promise<{ success: boolean; error?: string }> => {
    try {
      console.log('🔊 Generando audio IVR corporativo...');

      if (!this.isConfigured()) {
        console.warn('⚠️ ElevenLabs no configurado, no se puede generar audio');
        return { success: false, error: 'ElevenLabs API Key no configurada' };
      }

      // Mensaje IVR corporativo (mismo que en IVRMessageHelper.java)
      const ivrText = "Bienvenido al sistema de atención telefónica. " +
                      "Para ventas, pulse 1. " +
                      "Para soporte técnico, pulse 2. " +
                      "Para hablar con un operador, pulse 3. " +
                      "Para repetir este menú, pulse 9.";

      // Generar audio con ElevenLabs (usando voz de Manolo - abuelo)
      const ttsResult = await this.textToSpeech(ivrText, 'manolo');

      if (!ttsResult.success || !ttsResult.audioUri) {
        console.error('❌ Error generando audio IVR:', ttsResult.error);
        return { success: false, error: ttsResult.error };
      }

      // Extraer Base64 del data URI
      const base64Audio = ttsResult.audioUri.replace('data:audio/mpeg;base64,', '');

      // Importar el servicio dinámicamente (para evitar dependencias circulares)
      const ivrGeneratorService = require('./IVRGeneratorService').default;

      // Guardar en almacenamiento nativo
      const saveResult = await ivrGeneratorService.saveFromBase64(base64Audio);

      console.log('✅ Audio IVR corporativo guardado:', saveResult.path);
      console.log(`📊 Tamaño: ${(saveResult.size / 1024).toFixed(2)} KB`);

      return { success: true };

    } catch (error: any) {
      console.error('❌ Error generando audio IVR:', error);
      return { success: false, error: error.message };
    }
  };
}

// Crear instancia única
export const elevenLabsService = new ElevenLabsService();