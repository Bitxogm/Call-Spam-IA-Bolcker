// src/utils/permisoNotificaciones.ts
import { PermissionsAndroid, Platform } from 'react-native';

/**
 * Android 13+ (API 33) exige pedir POST_NOTIFICATIONS en tiempo de ejecución:
 * declararlo en el manifest no basta. Sin él, las notificaciones de spam de los
 * tres modos no se ven. En versiones anteriores no hay nada que pedir.
 */
export const pedirPermisoNotificaciones = async (): Promise<boolean> => {
  if (Platform.OS !== 'android' || Number(Platform.Version) < 33) {
    return true;
  }
  const permiso = PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS;
  try {
    if (await PermissionsAndroid.check(permiso)) {
      return true;
    }
    const resultado = await PermissionsAndroid.request(permiso);
    if (resultado !== PermissionsAndroid.RESULTS.GRANTED) {
      console.warn(`🔕 Permiso de notificaciones no concedido (${resultado})`);
    }
    return resultado === PermissionsAndroid.RESULTS.GRANTED;
  } catch (error) {
    console.error('❌ Error pidiendo el permiso de notificaciones:', error);
    return false;
  }
};
