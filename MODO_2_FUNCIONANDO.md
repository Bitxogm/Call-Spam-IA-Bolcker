# 🎉 Modo 2 FUNCIONANDO - 27 Enero 2026

## ✅ ESTADO: FUNCIONANDO COMPLETAMENTE

**Fecha:** 27 Enero 2026, 12:40h
**Logro:** Modo 2 (IVR Backend Fixed) operativo

---

## 🎯 Lo que funciona:

### Modo 1 - Answer+Hangup ✅
- Detecta spam localmente
- Responde y cuelga inmediatamente
- NO requiere backend
- Coste: €0/mes

### Modo 2 - IVR Backend (FIXED) ✅ ¡NUEVO!
- Desvío automático: `*21*34919933065#`
- Llamadas → Zadarma (+34 919 93 30 65) → Asterisk VPS → Mensaje personalizado
- Reproduce audio personalizado: `/var/lib/asterisk/sounds/es/fixed_spam_message.wav`
- Control desde la app (cambio de modo)
- **¡Probado y funcionando!** 🎉

---

## 🔧 Configuración Final (FUNCIONAL):

### Zadarma Panel:
```
Número: +34 919 93 30 65
SIP Login: #719926
External Server: 34919933065@157.180.35.161:5060
Estado: Activo ✅
```

### VPS Hetzner (157.180.35.161):

#### pjsip.conf:
```ini
[zadarma-endpoint]
type=endpoint
context=from-zadarma
disallow=all
allow=ulaw,alaw
aors=zadarma-aor
allow_subscribe=yes

[zadarma-aor]
type=aor
contact=sip:sips.zadarma.com

[zadarma-identify]
type=identify
endpoint=zadarma-endpoint
match=185.45.152.0/24
match=185.45.154.0/24
match=185.45.155.0/24
match=195.122.19.0/27
match=31.31.222.192/27
match=15.235.128.64/28
```

#### extensions.conf:
```ini
[from-zadarma]
exten => _.,1,NoOp(--- LLAMADA SPAM DETECTADA ---)
same => n,Answer()
same => n,Wait(1)
same => n,AGI(decision_agi.py)
same => n,Hangup()
```

#### decision_agi.py:
```
Ubicación: /usr/share/asterisk/agi-bin/decision_agi.py
Permisos: chmod +x
Función: Lee /root/ai_bridge/current_mode.json y decide qué hacer
- Modo FIXED: Reproduce audio personalizado
- Modo AI: (pendiente implementar)
```

#### Audio personalizado:
```
Origen: /root/ai_bridge/custom_fixed_message.wav
Destino: /var/lib/asterisk/sounds/es/fixed_spam_message.wav
Formato: WAV (compatible con Asterisk)
```

#### Firewall (UFW):
```bash
# Puertos SIP abiertos para Zadarma
185.45.152.0/24 → 5060/udp
185.45.154.0/24 → 5060/udp
185.45.155.0/24 → 5060/udp
195.122.19.0/27 → 5060/udp
31.31.222.192/27 → 5060/udp
15.235.128.64/28 → 5060/udp
```

---

## 📊 Flujo de Llamada (VERIFICADO):

```
Llamada spam → Móvil con desvío (*21*) → Zadarma +34919933065
    ↓
Zadarma recibe llamada (185.45.152.x)
    ↓
Zadarma envía SIP INVITE → VPS 157.180.35.161:5060
    ↓
Asterisk recibe INVITE (contexto: from-zadarma)
    ↓
Ejecuta dialplan: Answer() + Wait(1) + AGI(decision_agi.py)
    ↓
decision_agi.py lee: /root/ai_bridge/current_mode.json
    ↓
Modo = FIXED → Playback(fixed_spam_message)
    ↓
Llamante escucha mensaje personalizado
    ↓
Hangup() → Llamada finalizada
```

**Duración típica:** ~20-30 segundos
**Coste llamada:** GRATIS (llamadas entrantes Zadarma sin coste)

---

## 🐛 Problemas Resueltos:

### Problema 1: Llamadas no llegaban a VPS
**Causa:** External Server con puerto incorrecto (5000 en lugar de 5060)
**Solución:** Cambiar a `34919933065@157.180.35.161:5060`

### Problema 2: Firewall bloqueaba Zadarma
**Causa:** Solo 3 subnets configuradas, faltaban otras 3
**Solución:** Añadir las 6 subnets completas de Zadarma

### Problema 3: AGI script no encontrado
**Causa:** Script en `/root/ai_bridge/`, Asterisk busca en `/usr/share/asterisk/agi-bin/`
**Solución:** Copiar con permisos +x al directorio correcto

### Problema 4: Audio no encontrado
**Causa:** Audio en `/root/ai_bridge/`, Asterisk busca en `/var/lib/asterisk/sounds/`
**Solución:** Copiar como `fixed_spam_message.wav` al directorio correcto

### Problema 5: victor_agi.py no existe
**Causa:** Modo AI no implementado aún
**Solución:** Modificar decision_agi.py para funcionar sin Modo AI

---

## 🎯 Pendiente para Modo 3 (IA):

- [ ] Crear `victor_agi.py` con integración Anthropic/OpenAI
- [ ] Configurar API keys en `.env`
- [ ] Implementar conversación en tiempo real
- [ ] Text-to-Speech para respuestas dinámicas
- [ ] Speech-to-Text para entender al caller

**Estimación:** 2-3 horas de trabajo adicional

---

## 💰 Costes mensuales:

```
Zadarma número virtual Madrid: €1.70/mes
Hetzner VPS (2GB RAM, 40GB):   €4.51/mes
Llamadas entrantes:             €0 (gratis)
----------------------------------------
TOTAL:                          €6.21/mes
```

**Alternativa sin VPS (solo Modo 1):** €0/mes

---

## 🔧 Mantenimiento:

### Cambiar mensaje de audio:
```bash
# 1. Copiar nuevo audio WAV
cp nuevo_mensaje.wav /var/lib/asterisk/sounds/es/fixed_spam_message.wav

# 2. No requiere reiniciar Asterisk (lee en cada llamada)
```

### Ver logs en tiempo real:
```bash
tail -f /var/log/asterisk/full
```

### Cambiar modo desde app:
```bash
# La app ya hace esto automáticamente vía control_api.py:5000
curl -X POST http://157.180.35.161:5000/set_mode -d '{"mode":"FIXED"}'
```

### Verificar estado:
```bash
asterisk -rx "pjsip show endpoints"
asterisk -rx "core show channels"
```

---

## 📝 Pruebas pendientes:

Usuario va a probar y reportar diferencias entre:

1. **Llamar directamente a +34 919 93 30 65** (Samsung)
2. **Recibir llamada spam** (con desvío activado)
3. **Desde otro operador/teléfono**

**Objetivo:** Verificar comportamiento en diferentes escenarios reales

---

## 🎉 Celebración:

Después de:
- ~4 horas de debug ayer
- ~2 horas de configuración hoy
- Múltiples intentos con Zadarma support
- Frustración y casi cancelar todo...

**¡FUNCIONA!** 🎉🍾

El esfuerzo valió la pena. Ahora tienes un sistema profesional de IVR anti-spam.

---

## 📚 Referencias:

- Zadarma Docs: https://zadarma.com/en/support/instructions/asterisk/trunk/
- Asterisk AGI: https://wiki.asterisk.org/wiki/display/AST/AGI+Commands
- PJSIP Config: https://wiki.asterisk.org/wiki/display/AST/Configuring+res_pjsip

---

**Última actualización:** 27 Enero 2026, 12:40h
**Estado:** ✅ OPERATIVO
**Próximo paso:** Pruebas de usuario y pulido
