# 🔄 Estado del Proyecto - 26 Enero 2026 - 19:30h

## ✅ Lo que SÍ funciona:

- **Modo 1 (Answer+Hangup)**: ✅ Funciona perfectamente
  - Detecta llamadas spam
  - Las responde automáticamente
  - Las cuelga inmediatamente
  - NO necesita backend ni VPS
  - **Coste: €0/mes**

---

## 🔧 Lo que estamos intentando arreglar:

**Modo 2/3 (IVR con backend Zadarma + Asterisk)**

### Problema:
Las llamadas al número virtual +34 919 93 30 65 **NO llegan al VPS/Asterisk**.

### Diagnóstico realizado hoy:

1. ✅ Asterisk funciona correctamente
2. ✅ Logging habilitado en `/var/log/asterisk/full`
3. ✅ Puertos SIP (5060/5061) abiertos
4. ✅ Firewall configurado con IPs de Zadarma
5. ✅ `pjsip.conf` actualizado con todas las subnets de Zadarma
6. ❌ "External Server" configurado pero NO funciona
7. 🔄 **SIP Trunk configurado** (pendiente de verificar)

---

## 🎯 Para mañana (27 Enero):

### Paso 1: Verificar configuración SIP Trunk

**En panel Zadarma:**
- Ir a: My PBX → SIP Trunks
- Verificar que existe trunk con IP: 157.180.35.161
- Estado debe ser: "Conectado" o "Activo"

**En VPS:**
```bash
ssh root@157.180.35.161
asterisk -rx "pjsip show registrations"
```

Debe mostrar algo como:
```
zadarma-trunk    Registered
```

---

### Paso 2: Llamada de verificación al 8888

Zadarma puede requerir una llamada de verificación desde tu servidor para autorizar tu IP automáticamente.

**Comando:**
```bash
asterisk -rvvv
```

Luego dentro de la consola:
```
channel originate PJSIP/8888@zadarma-endpoint application Playback demo-congrats
```

Esto hace una llamada al número de prueba 8888 de Zadarma.

---

### Paso 3: Test final

**Terminal 1 (VPS):**
```bash
tail -f /var/log/asterisk/full | grep -E "185.45|195.122|31.31|15.235"
```

**Desde cualquier teléfono:**
```
Llamar a: +34 919 93 30 65
```

**Resultado esperado:**
Deberías ver en el log algo como:
```
[Jan 27 XX:XX:XX] NOTICE: New call from 185.45.152.XX
[Jan 27 XX:XX:XX] Executing [s@from-zadarma:1] AGI("PJSIP/...", "decision_agi.py")
```

---

## 📋 Configuración actual:

### Zadarma:
- Número virtual: +34 919 93 30 65 (España, Madrid)
- SIP login: #719926
- External Server: 34919933065@157.180.35.161:5060 (configurado)
- SIP Trunk: (configurado hoy, pendiente verificar)

### VPS Hetzner:
- IP: 157.180.35.161
- Puerto SIP: 5060 (abierto)
- Asterisk: Running
- Logging: Habilitado
- control_api.py: Puerto 5000 (corriendo)

### Firewall (UFW):
```
IPs autorizadas:
- 185.45.152.0/24
- 185.45.154.0/24
- 185.45.155.0/24
- 195.122.19.0/27
- 31.31.222.192/27
- 15.235.128.64/28
```

---

## 💡 Opción alternativa si SIP Trunk no funciona:

**Usar solo Modo 1** (ya funciona perfectamente):
- Cancelar número Zadarma (recuperar dinero restante)
- Pausar/Cancelar VPS Hetzner
- Seguir usando Modo 1 que ya bloquea spam efectivamente
- **Ahorro: ~€6/mes**

---

## 📞 Contactos de soporte:

**Zadarma Support:**
- Panel: https://my.zadarma.com
- Online chat: Disponible en panel
- Ya hablaste con: Leonardo y Sergio

**Si necesitas ayuda:**
- Mencionar que tienes "SIP Trunk configurado"
- Preguntar si ven llamadas salientes desde tu IP
- Pedir verificación de estado del trunk

---

## 🗂️ Archivos importantes:

### En el VPS:
- `/etc/asterisk/pjsip.conf` - Configuración SIP endpoints
- `/etc/asterisk/extensions.conf` - Dialplan
- `/var/log/asterisk/full` - Logs de Asterisk
- `/root/decision_agi.py` - Script AGI para modos
- `/root/control_api.py` - API Flask (puerto 5000)
- `/root/diagnostico_zadarma.sh` - Script de diagnóstico

### En el repositorio:
- `GUIA_DIAGNOSTICO_ZADARMA.md` - Guía de diagnóstico
- `CAUSAS_PROBABLES.md` - Análisis de problemas comunes
- `diagnostico_zadarma.sh` - Script automático

---

## 🎯 Próxima sesión:

1. ☕ Empieza fresco con energía
2. 🔍 Verifica estado del SIP Trunk
3. 📞 Haz la llamada de verificación al 8888
4. ✅ Test de llamada entrante
5. 🎉 Si funciona → Documenta y celebra
6. 🤔 Si NO funciona → Decide si usar solo Modo 1

---

**Nota importante:**
Modo 1 ya es una **solución completa y funcional** para bloquear spam.
Modo 2/3 son un "extra" con más funcionalidades, pero no son imprescindibles.

---

Creado: 26 Enero 2026, 19:30h
Estado: En pausa hasta mañana
Branch: claude/fix-call-permissions-01KAWsdwPsM88H55dEWN8Xqf
