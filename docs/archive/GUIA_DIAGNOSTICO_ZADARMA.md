# 🔍 Guía de Diagnóstico: Zadarma "Número No Disponible"

## 📋 Problema Actual

Cuando activas el desvío de llamadas con `*21*34919933065#`:
- ✅ El código USSD se ejecuta correctamente
- ✅ El operador confirma que el desvío está activo
- ❌ Pero al recibir una llamada, el operador dice "número no disponible"

Esto significa que **las llamadas NO están llegando al número de Zadarma**.

---

## 🎯 Objetivo del Diagnóstico

Identificar en qué punto falla la cadena:

```
Llamada entrante → Desvío (*21*) → Zadarma (+34 919933065) → Asterisk VPS (157.180.35.161) → AGI Scripts
```

---

## 🚀 Paso 1: Ejecutar Script de Diagnóstico en el VPS

He creado un script automático que:
1. Habilita el logging de Asterisk
2. Verifica la configuración de Zadarma
3. Comprueba puertos y firewall
4. Muestra el estado de conexión

### Cómo ejecutarlo:

```bash
# 1. Copiar el script al VPS
scp diagnostico_zadarma.sh root@157.180.35.161:/root/

# 2. Conectar al VPS
ssh root@157.180.35.161

# 3. Ejecutar el script
cd /root
chmod +x diagnostico_zadarma.sh
bash diagnostico_zadarma.sh
```

El script generará un informe completo del estado del sistema.

---

## 🔬 Paso 2: Test en Tiempo Real

Después de ejecutar el script de diagnóstico:

### En el VPS (Terminal 1):
```bash
tail -f /var/log/asterisk/full
```

### Desde tu teléfono (o cualquier otro):
**Llama directamente al número de Zadarma: +34 919 93 30 65**

### ¿Qué observar?

#### Escenario A: NO aparece NADA en el log
```
(El log no muestra ninguna actividad)
```
**Diagnóstico:** La llamada NO está llegando al VPS.
**Problema:** Configuración en el panel de Zadarma.

**Solución:**
1. Accede al panel de Zadarma (https://my.zadarma.com)
2. Ve a "Números virtuales" → +34 919 93 30 65
3. Verifica:
   - ✅ Estado: "Activo"
   - ✅ Destino: Debe apuntar a tu VPS `157.180.35.161`
   - ✅ Tipo: "PBX/SIP" (no "Reenvío simple")
   - ✅ SIP trunk: Debe estar configurado y activo

---

#### Escenario B: Aparece la llamada pero dice "No matching endpoint"
```
[from-zadarma] Received incoming call from sip:xxxxx@zadarma.com
WARNING: No matching endpoint found
```
**Diagnóstico:** La llamada llega al VPS pero Asterisk no reconoce a Zadarma.
**Problema:** Configuración de `pjsip.conf`.

**Solución:** Verificar que `/etc/asterisk/pjsip.conf` tenga:
```ini
[zadarma-identify]
type=identify
endpoint=zadarma-endpoint
match=185.45.152.0/24
match=198.143.191.0/24
match=37.139.129.0/24
```

---

#### Escenario C: Aparece la llamada pero falla en el dialplan
```
[from-zadarma] Executing Dial(...)
[from-zadarma] No such extension
```
**Diagnóstico:** La llamada llega pero el dialplan no sabe qué hacer.
**Problema:** Configuración de `extensions.conf`.

**Solución:** Verificar que `/etc/asterisk/extensions.conf` tenga el contexto `[from-zadarma]` correctamente configurado.

---

#### Escenario D: Todo funciona correctamente
```
[from-zadarma] Received incoming call
[from-zadarma] Executing AGI(decision_agi.py)
VICTOR_DECISION: Modo detectado: AI
```
**Diagnóstico:** ¡Todo funciona! 🎉

---

## 🔧 Paso 3: Verificar Panel de Zadarma

Accede a https://my.zadarma.com y verifica:

### 1. Estado del número
```
Números Virtuales → +34 919 93 30 65
Estado: Activo ✅
```

### 2. Configuración del destino
```
Destino: PBX/SIP
Servidor SIP: 157.180.35.161
Puerto: 5060
```

### 3. SIP Trunk
```
PBX → SIP Trunks
Estado: Conectado ✅
IP del servidor: 157.180.35.161
```

### 4. Registro de llamadas
```
Estadísticas → Registro de llamadas
¿Aparece tu número de prueba?
- SÍ → La llamada llegó a Zadarma (pero no al VPS)
- NO → El operador no está reenviando a Zadarma
```

---

## 🎯 Checklist de Diagnóstico

Completa este checklist paso a paso:

- [ ] Script `diagnostico_zadarma.sh` ejecutado en VPS
- [ ] Logging de Asterisk habilitado (`/var/log/asterisk/full` existe)
- [ ] Puertos 5060/5061 abiertos en firewall
- [ ] Llamada de prueba realizada a +34 919 93 30 65
- [ ] Log de Asterisk monitoreado durante la llamada
- [ ] Panel de Zadarma verificado (número activo, destino configurado)
- [ ] SIP trunk de Zadarma en estado "Conectado"

---

## 📞 Caso Especial: Si el Número NO Existe

Si al llamar a +34 919 93 30 65 desde cualquier teléfono sale "número no disponible", significa:

1. **El número virtual de Zadarma no está activo**
   - Verifica en el panel de Zadarma que el número está en estado "Activo"
   - Verifica que tienes saldo suficiente para mantener el número

2. **El número no está asignado correctamente**
   - Confirma que +34 919 93 30 65 es TU número en el panel
   - Algunos números requieren activación manual

3. **El operador español bloquea Zadarma**
   - Algunos operadores pueden bloquear números VoIP
   - Prueba desde OTRO operador (amigo, familiar, etc.)

---

## 🆘 Siguiente Paso

Después de ejecutar el script de diagnóstico y hacer la llamada de prueba, comparte:

1. **Output completo del script** `diagnostico_zadarma.sh`
2. **Lo que viste en el log** cuando llamaste al número
3. **Captura del panel de Zadarma** (configuración del número)

Con esa información podré identificar exactamente dónde está el problema. 🎯
