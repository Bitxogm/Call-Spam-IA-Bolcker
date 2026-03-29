# 🚀 Opciones Futuras para IVR Corporativo

**Fecha:** 12 de Diciembre, 2025
**Contexto:** Después de confirmar que IVR local no es viable

---

## 🎯 Objetivo

**Reproducir mensaje IVR al caller (spammer)** de manera que:
- ✅ El caller SÍ escuche el mensaje
- ✅ El usuario NO pierda funcionalidad de su teléfono
- ✅ Sea viable técnicamente
- ✅ Sea viable económicamente (si tiene costo)

---

## 📊 Comparativa de Opciones

| Opción | Viabilidad | Costo Mensual | Complejidad | Tiempo Setup |
|--------|-----------|---------------|-------------|--------------|
| **Modo 1: Solo Colgar** | ✅✅✅ | $0 | Baja | Ya hecho |
| **VoIP/Asterisk** | ✅✅ | $10-20 | Media | 2-3 días |
| **Twilio/Cloud** | ✅✅✅ | $20-50 | Baja | 1 día |
| **Root/ROM Custom** | ⚠️ | $0 | Muy Alta | Semanas |
| **IVR Local** | ❌ | $0 | - | Imposible |

---

## 🌟 Opción A: VoIP/Asterisk (Self-Hosted) ⭐ RECOMENDADA

### **Arquitectura:**

```
┌─────────────────────────────────────────────────────────┐
│                      TU APP ANDROID                      │
│                                                          │
│  1. Detecta spam (CallAccessibilityService)             │
│  2. NO contesta la llamada localmente                   │
│  3. Envía API call al servidor:                         │
│     POST /api/ivr-callback                              │
│     { "number": "667642379" }                           │
└────────────────┬────────────────────────────────────────┘
                 │
                 │ INTERNET
                 ↓
┌─────────────────────────────────────────────────────────┐
│              TU SERVIDOR ASTERISK (VPS)                 │
│                                                          │
│  4. Servidor recibe request                             │
│  5. Asterisk hace llamada SALIENTE al spammer:          │
│     Originate(SIP/provider/667642379)                   │
│  6. Caller ve llamada entrante desde tu número VoIP     │
│  7. Caller contesta (curiosidad)                        │
│  8. Asterisk reproduce IVR pregrabado                   │
│  9. Después de 30s, Asterisk cuelga                     │
└─────────────────────────────────────────────────────────┘
                 ↓
          ┌──────────────┐
          │    CALLER    │
          │  (SPAMMER)   │
          │              │
          │ ✅ Escucha   │
          │    el IVR    │
          └──────────────┘
```

### **Componentes Necesarios:**

**1. Servidor VPS:**
- **Proveedor:** Hetzner, DigitalOcean, Vultr, AWS Lightsail
- **Specs mínimas:** 1 vCPU, 1GB RAM, 25GB SSD
- **OS:** Debian 12 o Ubuntu 22.04 LTS
- **Costo:** $5-10/mes

**2. Software (GRATIS - Open Source):**
- **Asterisk:** Motor PBX
- **FreePBX:** Panel de administración web (opcional pero recomendado)
- **Nginx:** Proxy reverso para API REST
- **Certbot:** SSL gratis (Let's Encrypt)

**3. Proveedor VoIP/SIP:**
- **Opciones:**
  - VoIP.ms (Canadá) - muy popular
  - Twilio (USA) - más caro pero fácil
  - Zadarma (Europa) - económico
  - Bandwidth.com (USA)

- **Necesitas:**
  - Trunk SIP para llamadas salientes
  - Número DID entrante (opcional, para testing)

**4. Tu App Android:**
- **Cambios mínimos:**
  - Módulo HTTP client (Retrofit, OkHttp)
  - API call cuando detecta spam
  - NO necesita ser Default Dialer
  - NO necesita reproducir audio localmente

### **Configuración Asterisk (Dialplan):**

```ini
; /etc/asterisk/extensions.conf

[ivr-spam]
; Contexto para llamadas IVR a spammers

exten => _X.,1,NoOp(IVR Callback to ${EXTEN})
  same => n,Answer()
  same => n,Wait(1)
  same => n,Playback(custom/ivr-corporativo)
  same => n,Wait(1)
  same => n,Playback(custom/ivr-corporativo)  ; Repetir
  same => n,Wait(1)
  same => n,Hangup()
```

### **API Server (Node.js ejemplo):**

```javascript
// server.js
const express = require('express');
const { exec } = require('child_process');

const app = express();
app.use(express.json());

// Endpoint que llama tu app Android
app.post('/api/ivr-callback', (req, res) => {
  const { number } = req.body;

  // Validar número
  if (!/^\d{9,15}$/.test(number)) {
    return res.status(400).json({ error: 'Invalid number' });
  }

  // Generar llamada con Asterisk Manager Interface (AMI)
  // O con Asterisk CLI
  const cmd = `asterisk -rx "channel originate SIP/voipms/${number} extension ivr-spam@from-internal"`;

  exec(cmd, (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ error: 'Call failed' });
    }
    res.json({ success: true, message: 'IVR callback initiated' });
  });
});

app.listen(3000);
```

### **Costos Detallados:**

| Concepto | Proveedor | Costo Mensual | Costo por Uso |
|----------|-----------|---------------|---------------|
| **VPS** | Hetzner CPX11 | €4.51 (~$5) | - |
| **Número DID** | VoIP.ms | $0.85/mes | - |
| **Trunk SIP** | VoIP.ms | $0 (pay per use) | - |
| **Llamadas salientes** | VoIP.ms | - | $0.01-0.02/min |
| **Ancho de banda** | Incluido en VPS | - | - |
| **SSL Certificate** | Let's Encrypt | $0 (gratis) | - |

**Ejemplo real:**
- 100 llamadas spam/mes @ 30 segundos cada una = 50 minutos
- Costo llamadas: 50 min × $0.015 = $0.75/mes
- **Total: $5 (VPS) + $0.85 (DID) + $0.75 (llamadas) = $6.60/mes**

### **Ventajas:**

- ✅ **IVR funciona al 100%** (caller SÍ escucha)
- ✅ Tu app NO necesita ser Default Dialer
- ✅ Teclado de teléfono funciona normal
- ✅ Control total del IVR (puedes cambiar mensajes)
- ✅ Estadísticas en servidor
- ✅ Escalable (puede manejar muchas llamadas)
- ✅ Bajo costo (~$7/mes)

### **Desventajas:**

- ⚠️ Requiere servidor 24/7
- ⚠️ Setup inicial (1-2 días para quien no conoce Asterisk)
- ⚠️ Mantenimiento ocasional (updates, monitoring)
- 💰 Costo recurrente (aunque bajo)

### **Recursos de Aprendizaje:**

- [Asterisk: The Definitive Guide](https://www.asteriskdocs.org/)
- [FreePBX Documentation](https://wiki.freepbx.org/)
- [VoIP.ms Asterisk Configuration](https://wiki.voip.ms/article/Asterisk)
- [Tutorial: Asterisk + FreePBX en 30 minutos](https://nerdvittles.com/)

---

## ☁️ Opción B: Twilio/Cloud Service

### **Arquitectura:**

```
TU APP → Twilio API → Twilio hace llamada → Caller escucha IVR
```

### **Más Simple que Asterisk:**

**Setup:**
```javascript
// Tu app llama a Twilio API
const twilio = require('twilio');
const client = twilio(accountSid, authToken);

client.calls.create({
  from: '+1234567890',  // Tu número Twilio
  to: '+34667642379',    // Spammer
  url: 'https://tu-servidor.com/ivr.xml'  // TwiML con IVR
});
```

**IVR (TwiML):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say language="es-ES">
    Este número no admite llamadas comerciales.
    Por favor, elimine este número de su lista.
  </Say>
  <Play>https://tu-servidor.com/ivr-corporativo.mp3</Play>
  <Hangup/>
</Response>
```

### **Costos:**

| Concepto | Costo |
|----------|-------|
| **Número de teléfono** | $1/mes |
| **Llamadas salientes** | $0.02-0.05/min |
| **TwiML hosting** | $0 (hosting propio) |

**Ejemplo:**
- 100 llamadas @ 30s = 50 min × $0.03 = $1.50/mes
- **Total: $1 + $1.50 = $2.50/mes**

### **Ventajas:**

- ✅ **Más simple** que Asterisk (no necesitas VPS)
- ✅ **Setup en 1 hora**
- ✅ **Muy confiable** (99.99% uptime)
- ✅ **Buena documentación**
- ✅ Funciona igual que Asterisk para el caller

### **Desventajas:**

- ⚠️ Más caro que Asterisk (por llamada)
- ⚠️ Menos control (API de tercero)
- ⚠️ Vendor lock-in

---

## 🛠️ Opción C: Root + ROM Personalizada

### **Concepto:**

Con root, puedes:
- Acceder a APIs privadas de Android
- Inyectar audio directamente en call stream
- Usar reflexión para llamar métodos ocultos

### **Ejemplo (NO RECOMENDADO):**

```java
// Con root, podrías usar AudioSystem (API privada)
Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
Method method = audioSystemClass.getMethod("setForceUse", int.class, int.class);
method.invoke(null, FOR_COMMUNICATION, FORCE_SPEAKER);
```

### **Ventajas:**

- ✅ IVR local funcionaría
- ✅ Sin costos recurrentes

### **Desventajas:**

- ❌ **Requiere root** (la mayoría de usuarios NO tienen)
- ❌ **Rompe Google Play Store policies**
- ❌ **Difícil de distribuir**
- ❌ **Se rompe con updates de Android**
- ❌ **No es mantenible**
- ❌ **Problemas de seguridad**

**Conclusión:** NO viable para app pública.

---

## 🎯 Recomendación Final

### **Para Pruebas/Validación:**
1. **Empezar con Twilio** ($2-3/mes)
   - Setup en 1 día
   - Validar si la estrategia funciona
   - Fácil de implementar

2. **Si funciona bien, migrar a Asterisk** ($7/mes)
   - Más económico a largo plazo
   - Más control
   - Más profesional

### **Para Producción:**
- **< 500 llamadas/mes:** Twilio está bien
- **> 500 llamadas/mes:** Asterisk es más económico

### **Si es solo para ti:**
- Asterisk en VPS casero (Raspberry Pi)
- $0/mes (solo electricidad)

---

## 📋 Plan de Implementación (VoIP/Asterisk)

### **Fase 1: Setup Servidor (1 día)**
1. Contratar VPS (Hetzner $5/mes)
2. Instalar FreePBX (script automático)
3. Configurar firewall (puertos SIP/RTP)
4. Obtener certificado SSL

### **Fase 2: Proveedor VoIP (1 día)**
1. Crear cuenta VoIP.ms
2. Comprar DID (número)
3. Configurar trunk SIP en Asterisk
4. Probar llamada saliente

### **Fase 3: Dialplan IVR (1 día)**
1. Crear contexto `ivr-spam`
2. Subir audio MP3 a `/var/lib/asterisk/sounds/custom/`
3. Configurar extensión con Playback
4. Probar manualmente

### **Fase 4: API Server (1 día)**
1. Instalar Node.js + Express
2. Crear endpoint `/api/ivr-callback`
3. Integrar con Asterisk AMI
4. Securizar con API key

### **Fase 5: Integrar App (1 día)**
1. Agregar Retrofit a app Android
2. Crear `IVRApiService.java`
3. Llamar API cuando detecta spam
4. Probar end-to-end

**Total:** ~5 días (más si es primera vez con Asterisk)

---

## 🔗 Recursos Útiles

### **Tutoriales Completos:**
- [Asterisk + FreePBX Installation Guide](https://wiki.freepbx.org/display/FPG/Installing+FreePBX+17+on+Debian+12.0)
- [VoIP.ms Configuration with Asterisk](https://wiki.voip.ms/article/Asterisk)
- [Incredible PBX (Asterisk pre-configured)](https://nerdvittles.com/)

### **Proveedores VoIP:**
- [VoIP.ms](https://voip.ms/) - Muy popular, $0.0085/min a España
- [Twilio](https://www.twilio.com/voice) - Fácil API, más caro
- [Zadarma](https://zadarma.com/) - Europa, económico

### **Hosting VPS:**
- [Hetzner](https://www.hetzner.com/cloud) - €4.51/mes, muy bueno
- [DigitalOcean](https://www.digitalocean.com/) - $6/mes
- [Vultr](https://www.vultr.com/) - $6/mes

### **Comunidades:**
- [r/VOIP (Reddit)](https://reddit.com/r/VOIP)
- [Asterisk Community](https://community.asterisk.org/)
- [FreePBX Forums](https://community.freepbx.org/)

---

## 📝 Notas Finales

**Este documento debe actualizarse** con:
- Experiencias reales de implementación
- Costos reales observados
- Problemas encontrados y soluciones
- Mejoras y optimizaciones

**Fecha última actualización:** 12 Diciembre 2025
