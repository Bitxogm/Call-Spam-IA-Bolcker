import { NativeModules } from 'react-native';

export type BuildInfo = { buildTime: string; debug: boolean };

/** null si el APK no trae el módulo (es anterior al sello). */
export async function getBuildInfo(): Promise<BuildInfo | null> {
  const modulo = NativeModules.BuildInfoModule;
  if (!modulo?.getInfo) return null;
  try {
    return await modulo.getInfo();
  } catch {
    return null;
  }
}

/**
 * Texto del sello. `jsDev` es __DEV__: el bundle que build-fresh.sh mete en el APK es de
 * producción (false), así que true significa que el JS llega de Metro o de una caché.
 */
export function textoSello(info: BuildInfo | null, jsDev: boolean): string {
  const apk = info ? `APK compilado: ${info.buildTime}` : 'APK sin sello (anterior al 08/11)';
  const js = jsDev ? 'JS de Metro/caché, no el del APK' : 'JS del APK';
  return `${apk} · ${js}`;
}

/** Logs de Debug: en cualquier APK debug, aunque el bundle sea de producción. */
export function mostrarHerramientasDebug(info: BuildInfo | null, jsDev: boolean): boolean {
  return jsDev || info?.debug === true;
}
