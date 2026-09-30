---
paths: ["android/app/src/main/java/**"]
---

# Capa de llamadas Android — reglas y trampas

## Reglas

1. **No tocar `/android/` sin avisar primero.** Cualquier cambio en Java requiere rebuild completo (~2-5 min). Confirmar antes de proceder.

2. **Nunca borrar un `.java` sin verificar referencias en todo el proyecto.** Casos vivos: `IVRAudioPlayer` en `CallAccessibilityService.java` e `IVRMessageHelper` en `CallStateReceiver.java`. Borrarlos sin limpiar esas referencias rompe el build.

3. **No tocar `CallAccessibilityService`.** Es la única capa que consigue contestar y colgar en Modo 1. Ya se intentó sustituirlo por `CallStateReceiver` y falló en dispositivo real — ver lección 2 más abajo.

## Lección 2 — El Accessibility Service no es eliminable

**Síntoma:** tras quitar `CallAccessibilityService` y mover la lógica a `CallStateReceiver`, en dispositivo real la app **detectaba el spam pero no contestaba ni colgaba**.

**Causa:** `CallStateReceiver` está declarado en el manifest, y Android crea **una instancia nueva por cada broadcast**. Los campos de instancia (`lastIncomingNumber`, `wasRingingBeforeOffhook`) no sobreviven de RINGING a OFFHOOK, y `EXTRA_INCOMING_NUMBER` solo viaja en RINGING. Al llegar OFFHOOK el receiver no sabe a qué número pertenece la llamada, así que no cuelga. `CallScreeningService`, por su parte, **no puede contestar** una llamada por diseño.

**Regla:** Modo 1 depende del Accessibility Service. Si se vuelve a intentar quitarlo, el estado entre broadcasts tiene que persistirse (SharedPreferences), y aun así hay que **probarlo en dispositivo real** antes de dar nada por bueno. Revertido en `03782bc`.

## Valores hardcodeados

| Valor                  | Archivo                       | Notas                                           |
| ---------------------- | ----------------------------- | ----------------------------------------------- |
| `"919933065"`          | CallForwardingManager.java    | Número Zadarma, **sin prefijo de país**         |
| `*67*919933065#`       | CallForwardingManager.java    | USSD activar desvío (**67**, no 21)             |
| `##67#`                | CallForwardingManager.java    | USSD desactivar                                 |
| `*#67#`                | CallForwardingManager.java    | USSD consultar estado                           |
| `"/ivr_corporate.mp3"` | CallAccessibilityService.java | Ruta MP3 on-device — ya nadie genera el fichero |
| últimos 9 dígitos      | AnswerHangupHelper.java       | Normalización España                            |
