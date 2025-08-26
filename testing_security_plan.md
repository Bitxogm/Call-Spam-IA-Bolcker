# Plan de Testing y Seguridad - SpamBlocker App

## Estado Actual
- ✅ Código limpio (eliminado Twilio y micrófono)
- ✅ Funcionalidad básica trabajando en web
- ⏳ Pendiente: Auditoría de seguridad y testing pre-APK

## Fase 1: Auditoría de Código y Permisos

### 1.1 Revisión de Permisos Android
- [ ] Auditar `app.json` - verificar que solo se soliciten permisos necesarios
- [ ] Revisar `AndroidManifest.xml` - eliminar permisos innecesarios
- [ ] Documentar propósito de cada permiso solicitado
- [ ] Verificar que permisos críticos tienen justificación clara

### 1.2 Auditoría de Logs y Datos Sensibles
- [ ] Buscar logs que expongan números de teléfono
- [ ] Verificar que no hay API keys hardcodeadas en código
- [ ] Revisar que datos en SQLite no contengan información sensible sin cifrar
- [ ] Confirmar que `.env` está en `.gitignore`

### 1.3 Análisis de Superficie de Ataque
- [ ] Mapear qué datos procesa la app (números, contactos, conversaciones IA)
- [ ] Verificar mecanismos de almacenamiento (SQLite, variables en memoria)
- [ ] Confirmar que no hay endpoints HTTP expuestos
- [ ] Revisar comunicación con APIs externas (Gemini, ElevenLabs)

## Fase 2: Testing de Seguridad Automatizado

### 2.1 Análisis Estático
- [ ] Configurar ESLint con reglas de seguridad
- [ ] Ejecutar análisis con herramientas como:
  - `npm audit` para dependencias vulnerables
  - `semgrep` para patrones inseguros
  - Manual review de imports y dependencias

### 2.2 Testing de Base de Datos
- [ ] Verificar queries SQL (SQLite injection)
- [ ] Confirmar que datos sensibles están protegidos
- [ ] Testing de concurrencia en operaciones de DB
- [ ] Verificar integridad de datos tras crasheos

### 2.3 Testing de APIs
- [ ] Verificar manejo de errores de APIs externas
- [ ] Confirmar timeouts apropiados
- [ ] Testing de rate limiting (ElevenLabs tiene límites)
- [ ] Verificar que fallos de API no crashean la app

## Fase 3: Testing Funcional Pre-APK

### 3.1 Testing en Emulador
- [ ] Verificar todas las pantallas funcionan
- [ ] Testing de navegación completa
- [ ] Verificar persistencia de datos (reinicio app)
- [ ] Testing de modo radical con/sin permisos de contactos

### 3.2 Testing de Configuración
- [ ] Verificar botón "desactivar interceptor"
- [ ] Testing de cambio de personalidades IA
- [ ] Verificar que configuraciones persisten
- [ ] Testing de reset/limpieza de datos

### 3.3 Testing de Edge Cases
- [ ] App sin conexión a internet
- [ ] API keys inválidas o agotadas
- [ ] Memoria baja del dispositivo
- [ ] Interrupciones del sistema (llamadas, notificaciones)

## Fase 4: Generación y Testing de APK

### 4.1 Build de APK Seguro
- [ ] Generar APK debug con `./gradlew assembleDebug`
- [ ] Verificar tamaño del APK (< 100MB recomendado)
- [ ] Confirmar que no hay logs de debug en release
- [ ] Verificar firma digital del APK

### 4.2 Testing en Dispositivo Aislado
- [ ] Instalar APK en Android nativo
- [ ] Verificar todas las funcionalidades sin interceptor
- [ ] Testing de permisos: conceder/denegar/revocar
- [ ] Verificar que app solicita permisos apropiadamente

### 4.3 Testing de Permisos Críticos
- [ ] Verificar flujo de "app de marcador predeterminada"
- [ ] Confirmar que hay forma fácil de revertir
- [ ] Testing con permisos parciales (algunos sí, otros no)
- [ ] Verificar comportamiento sin permisos de contactos

## Fase 5: Testing de Interceptación Controlado

### 5.1 Preparación del Entorno
- [ ] Configurar dos números de prueba
- [ ] Documentar proceso de reversión completo
- [ ] Tener app de llamadas original lista para restaurar
- [ ] Configurar entorno de testing aislado

### 5.2 Testing de Interceptación Básico
- [ ] Activar interceptor con configuración mínima
- [ ] Probar llamada entre números propios
- [ ] Verificar que logs muestran interceptación correcta
- [ ] Confirmar que puede desactivarse inmediatamente

### 5.3 Testing de Casos Críticos
- [ ] Verificar que llamadas de emergencia NO se bloquean
- [ ] Testing con números en contactos (modo radical)
- [ ] Verificar comportamiento con llamadas múltiples
- [ ] Testing de recuperación tras crash durante llamada

### 5.4 Testing de Funcionalidad IA
- [ ] Verificar que IA responde apropiadamente
- [ ] Testing de TTS en dispositivo real
- [ ] Confirmar que conversaciones se almacenan correctamente
- [ ] Verificar limits de tokens/caracteres de APIs

## Fase 6: Testing de Seguridad en Producción

### 6.1 Análisis Post-Instalación
- [ ] Verificar que app no modifica configuraciones sistema
- [ ] Confirmar que datos solo se almacenan localmente
- [ ] Verificar que no hay comunicación no autorizada
- [ ] Testing de uninstall limpio (no deja rastros)

### 6.2 Testing de Persistencia
- [ ] Verificar comportamiento tras reinicio sistema
- [ ] Confirmar que configuraciones persisten
- [ ] Testing de updates de Android (compatibilidad)
- [ ] Verificar comportamiento con batería baja

## Criterios de Aceptación

### Seguridad
- [ ] Cero hardcoded secrets en código
- [ ] Solo permisos mínimos necesarios solicitados
- [ ] Datos sensibles cifrados o no almacenados
- [ ] Mecanismo confiable de desactivación

### Funcionalidad
- [ ] Todas las pantallas funcionan correctamente
- [ ] Interceptación de llamadas funciona de forma controlada
- [ ] IA y TTS funcionan en dispositivo real
- [ ] App puede desinstalarse completamente

### Robustez
- [ ] App no crashea con inputs inesperados
- [ ] Manejo apropiado de errores de red
- [ ] Recuperación tras interrupciones del sistema
- [ ] Comportamiento predecible con recursos limitados

## Notas de Implementación

### Herramientas Recomendadas
- **Análisis estático**: ESLint, npm audit, manual review
- **Testing**: Jest para lógica, manual para UI
- **Monitoreo**: Android Studio Logcat, ADB logs
- **Debugging**: Chrome DevTools para web, Android Studio para nativo

### Consideraciones Especiales
- **GrapheneOS**: Testing secundario tras validar en Android nativo
- **Dos números**: Usar para testing controlado, nunca números críticos
- **Reversibilidad**: Siempre documentar cómo deshacer cambios
- **Backup**: Tener plan de recuperación si algo falla

### Documentación Requerida
- Lista de permisos y justificación
- Proceso de instalación y desinstalación
- Guía de recuperación en caso de problemas
- Resultados de todos los tests realizados

## Cronograma Estimado
- **Fase 1-2**: 1 día (auditoría y análisis automatizado)
- **Fase 3**: 1 día (testing funcional)
- **Fase 4**: 0.5 días (APK y testing básico)
- **Fase 5**: 1 día (testing interceptación controlado)
- **Fase 6**: 0.5 días (validación final)

**Total estimado**: 4 días de testing riguroso antes de considerarlo seguro para uso personal.