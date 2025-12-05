# 📋 Plan de Prueba - Normalización de Números

## ✅ Cambios Implementados

### Commit: `6b3cadf`
**Título**: feat: Add phone number normalization to fix blacklist matching

**Archivos modificados**:
- `src/services/DataBaseService.ts`

**Cambios realizados**:

1. **Función `normalizePhoneNumber()`** (líneas 37-50)
   - Elimina todos los caracteres no numéricos
   - Toma los últimos 9 dígitos (formato español)
   - Ejemplo: "+34 612 345 678" → "612345678"

2. **Actualizado `addSpamNumber()`** (líneas 125-144)
   - Normaliza el número ANTES de guardarlo en la base de datos
   - Log: "✅ Número [original] normalizado a [normalizado] y añadido"

3. **Actualizado `isSpamNumber()`** (líneas 182-203)
   - Normaliza el número ANTES de comparar con la base de datos
   - Log: "🔍 ¿[original] (normalizado: [normalizado]) es spam? ✅/❌"

## 🎯 Objetivo

Asegurar que los números en la lista negra se detecten correctamente sin importar el formato:

| Formato Guardado | Formato Llamada Entrante | ¿Debe Coincidir? |
|------------------|-------------------------|------------------|
| +34 612 345 678 | 612345678 | ✅ SÍ |
| 612345678 | +34612345678 | ✅ SÍ |
| +34-612-345-678 | 612 345 678 | ✅ SÍ |
| 900123456 | +34900123456 | ✅ SÍ |

## 🧪 Plan de Pruebas

### Prerequisito: Compilar APK

**Problema actual**: Error de red bloqueando compilación
- `expo export` falla: "No platforms configured"
- Gradle no puede descargar: "UnknownHostException: services.gradle.org"

**Solución temporal**:
1. Compilar en tu máquina local (con buena conexión)
2. O esperar a que se resuelva el problema de red en el entorno actual

### Prueba 1: Verificar Normalización al Guardar ✅

**Pasos**:
1. Abrir SpamBlocker app
2. Ir a "Añadir Número Spam"
3. Probar agregar números en DIFERENTES formatos:
   ```
   +34 612 345 678
   612345678
   +34-612-345-678
   612 345 678
   ```
4. **Verificar logs** (adb logcat o consola)
5. **Buscar mensaje**: "✅ Número ... normalizado a 612345678"

**Resultado esperado**:
- Todos deberían normalizarse a: `612345678`
- Solo debería guardarse UNA entrada en la base de datos

### Prueba 2: Verificar Detección en Lista Negra 🎯

**Setup**:
1. Añadir tu número de prueba a la lista negra: `+34 612 345 678`
2. Verificar que se guardó normalizado: `612345678`

**Pasos**:
1. Llamar desde ese número al teléfono con SpamBlocker
2. Monitorear logs durante la llamada
3. **Buscar mensaje**: "🔍 ¿... (normalizado: 612345678) es spam? ✅ SÍ"

**Resultado esperado**:
- El log debe mostrar: "✅ SÍ" (es spam)
- ⚠️ NOTA: En Samsung probablemente no se ejecute CallScreeningService
  - Pero podemos verificar que la lógica de normalización funciona

### Prueba 3: Verificar NO Falsos Positivos ⚠️

**Setup**:
1. Lista negra contiene: `612345678`
2. Número de prueba diferente: `612345679` (último dígito diferente)

**Pasos**:
1. Llamar desde `612345679`
2. **Buscar mensaje**: "🔍 ¿... (normalizado: 612345679) es spam? ❌ NO"

**Resultado esperado**:
- El log debe mostrar: "❌ NO" (no es spam)
- Solo números EXACTAMENTE iguales (después de normalizar) deben coincidir

### Prueba 4: Números Internacionales Largos 🌍

**Setup**:
1. Añadir número con código de país largo: `+1 234 567 8901` (11 dígitos)

**Pasos**:
1. Verificar normalización
2. **Buscar mensaje**: "📞 Normalizado: ... → 678901234"

**Resultado esperado**:
- Debe tomar los **últimos 9 dígitos**: `678901234`
- ⚠️ Esto es para números españoles (9 dígitos)
- Otros países podrían necesitar ajuste

### Prueba 5: Números con Prefijos Especiales 📞

**Setup**:
Añadir números de tarificación especial que ya están en CallScreeningService:

```
900123456  (Tarificación especial)
901234567  (Tarificación especial)
902345678  (Tarificación especial)
```

**Pasos**:
1. Añadir a lista negra
2. Verificar normalización
3. Simular llamada de esos números

**Resultado esperado**:
- Deben normalizarse correctamente
- Deben detectarse como spam

## 📊 Tabla de Resultados

| Prueba | Formato Entrada | Normalizado Esperado | Resultado | Observaciones |
|--------|----------------|---------------------|-----------|---------------|
| 1.1 | +34 612 345 678 | 612345678 | ⬜ | |
| 1.2 | 612345678 | 612345678 | ⬜ | |
| 1.3 | +34-612-345-678 | 612345678 | ⬜ | |
| 1.4 | 612 345 678 | 612345678 | ⬜ | |
| 2 | Llamada real | Detectado como spam | ⬜ | |
| 3 | Número diferente | NO detectado | ⬜ | |
| 4 | +1 234 567 8901 | 678901234 | ⬜ | |
| 5 | 900123456 | 900123456 | ⬜ | |

**Leyenda**: ⬜ Pendiente | ✅ Éxito | ❌ Fallo

## 🐛 Problemas Conocidos

### Samsung Galaxy Note 20
- CallScreeningService bloqueado por Samsung
- Los logs de `isSpamNumber()` solo se verán si CallScreeningService se ejecuta
- **Alternativa**: Probar en GrapheneOS o Android stock

### Red Compilation
- Entorno actual tiene problemas de red
- Compilar localmente o resolver problema de DNS

## 📝 Logs Importantes

**Para debugging**, buscar estos mensajes en `adb logcat`:

```bash
# Normalización
adb logcat | grep "📞 Normalizado"

# Verificación de spam
adb logcat | grep "🔍 ¿"

# Añadir a base de datos
adb logcat | grep "✅ Número"

# CallScreeningService (si funciona)
adb logcat | grep "CallScreeningService"
```

## 🔄 Siguiente Paso Después de Pruebas

Una vez verificado que la normalización funciona:

1. **Si Samsung sigue bloqueando CallScreeningService**:
   - Implementar Plan B: BroadcastReceiver con PHONE_STATE
   - O Plan D: Modo manual puro (usuario toca botón)

2. **Si funciona en GrapheneOS**:
   - Documentar incompatibilidad Samsung
   - Considerar solo soportar Android stock

3. **Integrar con CallScreeningService**:
   - Actualizar `shouldShowNotification()` en CallScreeningServiceImpl.java
   - Llamar a `databaseService.isSpamNumber()` desde Java
   - Necesitamos bridge TypeScript ↔ Java

## ✅ Estado Actual

- ✅ Código implementado y commiteado (commit `6b3cadf`)
- ✅ Pusheado a branch `claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf`
- ⏳ Pendiente: Compilar APK (problema de red)
- ⏳ Pendiente: Probar en dispositivo real
- ⏳ Pendiente: Integrar con CallScreeningService (Java)

---

**Fecha**: 05-Dic-2024
**Branch**: claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf
**Commit**: 6b3cadf
