// src/services/IVRGeneratorService.ts
import { NativeModules } from 'react-native';

const { IVRGeneratorModule } = NativeModules;

export interface IVRAudioInfo {
  exists: boolean;
  path?: string;
  size?: number;
}

export interface SaveResult {
  success: boolean;
  path: string;
  size: number;
}

/**
 * Servicio para gestionar audio IVR pre-generado
 * Usa ElevenLabs para generar audio de alta calidad
 */
class IVRGeneratorService {
  /**
   * Guarda audio IVR desde URL (ElevenLabs streaming)
   */
  async saveFromURL(audioURL: string): Promise<SaveResult> {
    try {
      console.log('📥 Guardando audio IVR desde URL...');
      const result = await IVRGeneratorModule.saveIVRAudio(audioURL);
      console.log('✅ Audio IVR guardado:', result);
      return result;
    } catch (error) {
      console.error('❌ Error guardando audio IVR:', error);
      throw error;
    }
  }

  /**
   * Guarda audio IVR desde Base64
   */
  async saveFromBase64(base64Audio: string): Promise<SaveResult> {
    try {
      console.log('💾 Guardando audio IVR desde Base64...');
      const result = await IVRGeneratorModule.saveIVRAudioFromBase64(base64Audio);
      console.log('✅ Audio IVR guardado:', result);
      return result;
    } catch (error) {
      console.error('❌ Error guardando audio IVR:', error);
      throw error;
    }
  }

  /**
   * Verifica si el audio IVR existe
   */
  async checkExists(): Promise<IVRAudioInfo> {
    try {
      const result = await IVRGeneratorModule.checkIVRAudioExists();
      return result;
    } catch (error) {
      console.error('❌ Error verificando audio IVR:', error);
      return { exists: false };
    }
  }

  /**
   * Elimina el audio IVR guardado
   */
  async delete(): Promise<boolean> {
    try {
      const result = await IVRGeneratorModule.deleteIVRAudio();
      console.log('🗑️ Audio IVR eliminado');
      return result;
    } catch (error) {
      console.error('❌ Error eliminando audio IVR:', error);
      return false;
    }
  }
}

export default new IVRGeneratorService();
