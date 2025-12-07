# 📞 Call Spam IA Blocker

**Bloqueador inteligente de llamadas spam para Android con IA integrada**

Una aplicación Android moderna desarrollada en React Native que detecta y bloquea automáticamente llamadas spam utilizando múltiples estrategias de verificación, incluyendo listas negras personalizables, verificación de contactos, modo radical y la innovadora técnica **Answer+Hangup**.

---

## 🌟 Características Principales

### ✅ Implementadas

- **🚫 Lista Negra Personalizable**
  - Añade números manualmente a la lista negra
  - Bloqueo instantáneo de números marcados como spam
  - Prioridad máxima en la verificación

- **👥 Whitelist Automática de Contactos**
  - Integración con PhoneLookup API de Android
  - Normalización automática de formatos de número
  - Consultas en tiempo real sin overhead de base de datos

- **📵 Modo Radical**
  - Bloquea TODAS las llamadas excepto tus contactos
  - Ideal para situaciones de alto spam
  - Configurable con un solo toggle

- **🔇 Answer+Hangup (Innovador)**
  - Contesta automáticamente llamadas spam de forma silenciosa
  - Espera un delay configurable (1-5 segundos)
  - Cuelga automáticamente la llamada
  - **Efecto**: Los spammers te quitan de sus listas (reducción ~80% spam)
  - Basado en la técnica exitosa de [SpamBlocker](https://github.com/aj3423/SpamBlocker)

- **🔔 Sistema de Notificaciones**
  - Notificaciones en tiempo real de spam detectado
  - Toast informativos con nombre de contacto (si aplica)
  - Feedback visual del estado de bloqueo

- **🎯 Verificación por Prioridad**
  1. 🚫 Lista Negra (máxima prioridad)
  2. 👤 Contactos (whitelist)
  3. 📵 Modo Radical
  4. 💰 Números premium (905...)
  5. ❓ Números desconocidos/privados

### 🚀 Roadmap (Próximas Features)

- **📡 API Racing de Bases de Datos de Spam**
  - Integración con PhoneBlock
  - Integración con Tellows
  - Integración con Should I Answer
  - Consultas paralelas para máxima velocidad

- **🤖 Análisis IA con Gemini**
  - Detección inteligente de patrones de spam
  - Análisis de comportamiento de llamadas
  - Mejora continua del modelo

- **📜 STIR/SHAKEN Attestation**
  - Verificación de autenticidad de llamadas
  - Detección de spoofing

- **🔍 Sistema de Reglas Regex**
  - Patrones personalizables para detección
  - Reglas por prefijos, sufijos, longitud

---

## 📋 Requisitos

- **Android**: 9.0 (API 28) o superior
- **Permisos requeridos**:
  - `READ_PHONE_STATE` - Monitorear estado de llamadas
  - `READ_CALL_LOG` - Acceso a registro de llamadas
  - `ANSWER_PHONE_CALLS` - Contestar llamadas automáticamente (Answer+Hangup)
  - `READ_CONTACTS` - Verificación de whitelist
  - `POST_NOTIFICATIONS` - Notificaciones de spam detectado

- **Configuración necesaria**:
  - Establecer la app como **Call Screening Service predeterminado**

---

## 🔧 Instalación

### Desde Código Fuente

1. **Clonar el repositorio**:
```bash
git clone https://github.com/Bitxogm/Call-Spam-IA-Bolcker.git
cd Call-Spam-IA-Bolcker
```

2. **Instalar dependencias**:
```bash
npm install
```

3. **Configurar variables de entorno**:
Crea un archivo `.env` en la raíz del proyecto:
```env
EXPO_PUBLIC_GEMINI_API_KEY=tu_api_key_aqui
EXPO_PUBLIC_GEMINI_MODEL=gemini-pro
```

4. **Compilar APK**:
```bash
./build-fresh.sh
```

5. **Instalar en dispositivo**:
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Desde APK Pre-compilado

1. Descarga el último APK desde [Releases](https://github.com/Bitxogm/Call-Spam-IA-Bolcker/releases)
2. Habilita "Instalar apps de origen desconocido" en tu Android
3. Instala el APK

---

## 🎮 Uso

### Primera Configuración

1. **Conceder Permisos**:
   - Abre la app
   - Presiona "Solicitar Permisos"
   - Acepta todos los permisos solicitados

2. **Establecer como Screening Service**:
   - Ve a Configuración → Apps → Apps predeterminadas → ID de llamadas y spam
   - Selecciona "Call Spam IA Blocker"

3. **Configurar Preferencias**:
   - **Modo Radical**: Activa para bloquear todo excepto contactos
   - **Answer+Hangup**: Activa para el bloqueo silencioso
   - **Delay de Hangup**: Ajusta 1-5 segundos según preferencia

### Añadir Números a Lista Negra

```
1. Ve a la pantalla "Lista Negra"
2. Escribe el número (formato: +34123456789 o 123456789)
3. Presiona "Añadir a Lista Negra"
```

### Probar el Sistema

```
1. Añade tu propio número a la lista negra
2. Llama desde otro teléfono
3. Observa:
   ✅ Notificación de spam detectado
   ✅ (Si Answer+Hangup activo) Llamada contestada y colgada automáticamente
   ✅ Toast informativo
```

---

## 🏗️ Arquitectura Técnica

### Stack Tecnológico

- **Frontend**: React Native + Expo
- **Backend/Nativo**: Java (Android)
- **Storage**: SharedPreferences
- **APIs Android**:
  - CallScreeningService (Android 10+)
  - TelecomManager
  - PhoneLookup API
  - BroadcastReceiver (PHONE_STATE)

### Estructura del Proyecto

```
Call-Spam-IA-Bolcker/
├── android/
│   └── app/src/main/java/com/anonymous/SpamBlockerApp/
│       ├── CallScreeningServiceImpl.java    # Servicio principal de screening
│       ├── CallStateReceiver.java           # Monitor de estados de llamada
│       ├── BlacklistModule.java             # Bridge RN para lista negra
│       ├── ContactsModule.java              # Bridge RN para contactos
│       ├── AnswerHangupModule.java          # Bridge RN para Answer+Hangup
│       ├── SharedPreferencesHelper.java     # Gestión de persistencia
│       ├── ContactsHelper.java              # PhoneLookup wrapper
│       └── AnswerHangupHelper.java          # Lógica Answer+Hangup
├── src/
│   ├── screens/
│   │   ├── HomeScreen.tsx                   # Pantalla principal
│   │   ├── BlacklistScreen.tsx              # Gestión de lista negra
│   │   └── WhitelistScreen.tsx              # Configuración whitelist/Answer+Hangup
│   └── services/
│       ├── CallInterceptorService.ts        # Servicio principal TS
│       ├── BlacklistService.ts              # Wrapper lista negra
│       ├── ContactsService.ts               # Wrapper contactos
│       └── AnswerHangupService.ts           # Wrapper Answer+Hangup
└── App.tsx                                   # Punto de entrada React Native
```

### Flujo de Verificación de Llamadas

```
┌─────────────────────────────────────────────────────────────┐
│  1. Llamada entrante                                        │
│     CallScreeningService.onScreenCall()                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Verificación por PRIORIDAD                              │
│     ┌─────────────────────────────────────────────────┐    │
│     │ ¿En Lista Negra? → SÍ → BLOQUEAR (prioridad 1) │    │
│     └─────────────────────────────────────────────────┘    │
│     ┌─────────────────────────────────────────────────┐    │
│     │ ¿Es Contacto? → SÍ → PERMITIR (prioridad 2)    │    │
│     └─────────────────────────────────────────────────┘    │
│     ┌─────────────────────────────────────────────────┐    │
│     │ ¿Modo Radical? → SÍ → BLOQUEAR (prioridad 3)   │    │
│     └─────────────────────────────────────────────────┘    │
│     ┌─────────────────────────────────────────────────┐    │
│     │ ¿Premium (905...)? → SÍ → BLOQUEAR (prior. 4)  │    │
│     └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Si es SPAM y Answer+Hangup ACTIVO:                     │
│     a) setSilenceCall(true) - Silenciar llamada            │
│     b) Marcar número en SharedPreferences                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  4. CallStateReceiver detecta OFFHOOK (contestada)         │
│     a) Verifica si número está marcado                      │
│     b) Espera delay configurado (1-5s)                      │
│     c) TelecomManager.endCall() - Colgar                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🤝 Colaboradores

### Autor Principal
- **[@Bitxogm](https://github.com/Bitxogm)** - Ideación, desarrollo, testing y mantenimiento

### Colaboradores Técnicos
- **Claude (Anthropic)** - Asistencia en arquitectura, implementación de features y debugging

---

## 🌐 Cómo Colaborar

¡Las contribuciones son bienvenidas! Si quieres colaborar:

### 🐛 Reportar Bugs

1. Abre un [Issue](https://github.com/Bitxogm/Call-Spam-IA-Bolcker/issues)
2. Describe el problema detalladamente
3. Incluye:
   - Versión de Android
   - Logs (si es posible)
   - Pasos para reproducir

### ✨ Proponer Features

1. Abre un [Issue](https://github.com/Bitxogm/Call-Spam-IA-Bolcker/issues) con etiqueta `enhancement`
2. Describe la funcionalidad propuesta
3. Explica el caso de uso

### 🔧 Contribuir Código

1. **Fork** el repositorio
2. Crea una rama para tu feature:
   ```bash
   git checkout -b feature/mi-nueva-feature
   ```
3. Realiza tus cambios y commits:
   ```bash
   git commit -m "feat: Descripción de la feature"
   ```
4. Push a tu fork:
   ```bash
   git push origin feature/mi-nueva-feature
   ```
5. Abre un **Pull Request**

#### Convención de Commits

Utilizamos [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` Nueva funcionalidad
- `fix:` Corrección de bug
- `docs:` Cambios en documentación
- `refactor:` Refactorización de código
- `test:` Añadir o modificar tests
- `chore:` Tareas de mantenimiento

---

## 📄 Licencia

Este proyecto está bajo la licencia **MIT**.

```
MIT License

Copyright (c) 2024 Bitxogm

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 🙏 Agradecimientos

- **[aj3423/SpamBlocker](https://github.com/aj3423/SpamBlocker)** - Inspiración para la técnica Answer+Hangup
- **Comunidad React Native** - Por el excelente framework
- **Expo Team** - Por las herramientas de desarrollo

---

## 📞 Contacto y Soporte

- **Issues**: [GitHub Issues](https://github.com/Bitxogm/Call-Spam-IA-Bolcker/issues)
- **Email**: [Crear issue para contacto]

---

## ⚠️ Descargo de Responsabilidad

Esta aplicación está diseñada para uso personal y educativo. El autor no se hace responsable de:
- Llamadas legítimas bloqueadas incorrectamente
- Llamadas spam no detectadas
- Cualquier daño o pérdida derivada del uso de la aplicación

**Recomendación**: Revisa periódicamente tu lista negra y configuración para asegurar que solo se bloquean números no deseados.

---

## 📊 Estado del Proyecto

![Estado](https://img.shields.io/badge/Estado-En%20Desarrollo%20Activo-brightgreen)
![Licencia](https://img.shields.io/badge/Licencia-MIT-blue)
![Android](https://img.shields.io/badge/Android-9.0%2B-green)
![React Native](https://img.shields.io/badge/React%20Native-Expo-blue)

**Última actualización**: Diciembre 2024

---

<div align="center">
  <p>Desarrollado con ❤️ para combatir el spam telefónico</p>
  <p>⭐ Si este proyecto te resulta útil, considera darle una estrella en GitHub</p>
</div>
