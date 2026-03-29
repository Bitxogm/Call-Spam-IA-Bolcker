# 🔍 Causas Probables: "Número No Disponible"

Basándome en tu configuración actual, estas son las causas MÁS PROBABLES ordenadas por frecuencia:

---

## 1. 🥇 CAUSA MÁS PROBABLE: Número de Zadarma sin destino PBX configurado

### Síntomas:
- El desvío `*21*` se activa correctamente
- Al llamar aparece "número no disponible"
- El log de Asterisk NO muestra ninguna actividad

### ¿Por qué sucede?
Zadarma tiene el número activo, pero **no sabe a dónde enviar las llamadas entrantes**.

### Cómo verificar:
1. Entra en https://my.zadarma.com
2. Ve a **Números Virtuales** → +34 919 93 30 65
3. Busca la sección **"Destino de las llamadas"** o **"Call routing"**
4. Debe decir algo como:
   - Tipo: **PBX/SIP** o **Servidor SIP**
   - Dirección: **157.180.35.161:5060**

### Cómo solucionarlo:
```
Panel Zadarma → Números Virtuales → +34 919 93 30 65 → Editar
Tipo de destino: PBX/SIP
Servidor SIP: 157.180.35.161
Puerto: 5060
Guardar cambios
```

**Test:** Llama al número desde cualquier teléfono. Si escuchas tono de llamada pero no responde, ¡progreso! Significa que ya llega al VPS.

---

## 2. 🥈 CAUSA PROBABLE: SIP Trunk de Zadarma no configurado

### Síntomas:
- El número tiene destino PBX configurado
- Pero el log de Asterisk no muestra actividad
- En el panel de Zadarma, el SIP trunk aparece como "Desconectado"

### ¿Por qué sucede?
Zadarma necesita un **SIP trunk** activo para comunicarse con tu Asterisk.

### Cómo verificar:
1. Panel Zadarma → **PBX** → **SIP Trunks**
2. Debe existir un trunk con:
   - IP: 157.180.35.161
   - Estado: **Conectado** ✅
   - Puerto: 5060

### Cómo solucionarlo:

#### Si no existe el trunk:
```
Crear nuevo SIP Trunk:
- Nombre: asterisk-vps
- IP: 157.180.35.161
- Puerto: 5060
- Autenticación: Sin autenticación (IP-based)
```

#### Si existe pero está desconectado:
1. Verifica que el puerto 5060 esté abierto en tu VPS:
   ```bash
   netstat -tulpn | grep 5060
   ```
2. Verifica el firewall:
   ```bash
   ufw allow 5060/udp
   ufw allow 5061/tcp
   ```
3. Reinicia el trunk en el panel de Zadarma

---

## 3. 🥉 CAUSA MENOS PROBABLE: Firewall bloqueando Zadarma

### Síntomas:
- El destino PBX está configurado
- El SIP trunk aparece como "Conectado"
- Pero el log de Asterisk NO muestra llamadas entrantes

### ¿Por qué sucede?
El firewall del VPS está bloqueando las llamadas SIP entrantes desde Zadarma.

### Cómo verificar:
```bash
# En el VPS
iptables -L -n | grep 5060
ufw status | grep 5060
```

### Cómo solucionarlo:
```bash
# Opción 1: UFW
ufw allow 5060/udp comment 'SIP Zadarma'
ufw allow 5061/tcp comment 'SIP TLS'

# Opción 2: iptables directo
iptables -A INPUT -p udp --dport 5060 -j ACCEPT
iptables -A INPUT -p tcp --dport 5061 -j ACCEPT
iptables-save > /etc/iptables/rules.v4

# Reiniciar firewall
systemctl restart ufw
```

**Importante:** También permite las IPs de Zadarma:
```bash
ufw allow from 185.45.152.0/24 comment 'Zadarma IP range 1'
ufw allow from 198.143.191.0/24 comment 'Zadarma IP range 2'
ufw allow from 37.139.129.0/24 comment 'Zadarma IP range 3'
```

---

## 4. 💡 OTRAS CAUSAS POSIBLES

### A. Número no activado
- **Síntoma:** El panel muestra el número pero en estado "Inactivo"
- **Solución:** Activar el número (puede requerir pago)

### B. Saldo insuficiente
- **Síntoma:** El número estaba activo pero dejó de funcionar
- **Solución:** Recargar saldo en Zadarma

### C. Configuración de `pjsip.conf` incorrecta
- **Síntoma:** El log muestra "No matching endpoint" cuando llega la llamada
- **Solución:** Verificar que las IPs de Zadarma estén en `[zadarma-identify]`

### D. Contexto dialplan no existe
- **Síntoma:** El log muestra "No such context 'from-zadarma'"
- **Solución:** Crear el contexto en `/etc/asterisk/extensions.conf`

---

## 🎯 Orden de Verificación Recomendado

Sigue este orden para diagnosticar eficientemente:

### ✅ Paso 1: Verificar Panel de Zadarma (2 minutos)
```
¿El número +34 919933065 tiene configurado un destino PBX?
SÍ → Ir al paso 2
NO → ESTE ES EL PROBLEMA → Configurar destino PBX
```

### ✅ Paso 2: Verificar SIP Trunk (2 minutos)
```
¿Existe un SIP trunk activo apuntando a 157.180.35.161?
SÍ → Ir al paso 3
NO → ESTE ES EL PROBLEMA → Crear/activar SIP trunk
```

### ✅ Paso 3: Ejecutar script de diagnóstico (5 minutos)
```bash
bash diagnostico_zadarma.sh
```
Esto verificará firewall, puertos, y configuración de Asterisk.

### ✅ Paso 4: Test de llamada con logging (1 minuto)
```bash
# Terminal 1 (VPS)
tail -f /var/log/asterisk/full

# Terminal 2 (tu teléfono)
Llamar a +34 919 93 30 65
```

¿Aparece algo en el log?
- **NO** → El problema está en Zadarma (paso 1 o 2)
- **SÍ** → El problema está en Asterisk (paso 5)

### ✅ Paso 5: Verificar configuración de Asterisk (si llegamos aquí)
```bash
asterisk -rx "pjsip show endpoints"
grep -A 10 "zadarma" /etc/asterisk/pjsip.conf
grep -A 10 "from-zadarma" /etc/asterisk/extensions.conf
```

---

## 📊 Resumen Visual

```
┌─────────────────────────────────────────────────┐
│  Llamada entrante al móvil                      │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  *21*34919933065# (Desvío activo) ✅           │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  Zadarma (+34 919 93 30 65)                     │
│  ¿Número activo? ←──────────── 🔍 VERIFICAR 1  │
│  ¿Destino PBX configurado? ←── 🔍 VERIFICAR 2  │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  SIP Trunk Zadarma → VPS                        │
│  ¿Trunk activo? ←────────────── 🔍 VERIFICAR 3 │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  VPS (157.180.35.161:5060)                      │
│  ¿Firewall permite 5060? ←───── 🔍 VERIFICAR 4 │
│  ¿Asterisk escuchando? ←──────── 🔍 VERIFICAR 5 │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  Asterisk pjsip.conf                            │
│  ¿Endpoint configurado? ←──────── 🔍 VERIFICAR 6│
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  extensions.conf [from-zadarma]                 │
│  ¿Contexto existe? ←───────────── 🔍 VERIFICAR 7│
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  AGI: decision_agi.py                           │
│  ¿Se ejecuta correctamente? ←─── 🔍 VERIFICAR 8│
└─────────────────────────────────────────────────┘
```

**En el 90% de los casos, el problema está en los pasos 1-2 (Panel de Zadarma).**

---

## 🚀 Acción Inmediata

**Lo primero que debes hacer AHORA MISMO:**

1. Accede a https://my.zadarma.com
2. Ve a **Números Virtuales**
3. Haz clic en +34 919 93 30 65
4. Toma una **captura de pantalla** de la configuración

Comparte esa captura y sabré exactamente cuál es el problema. 📸
