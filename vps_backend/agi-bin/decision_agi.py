#!/usr/bin/env python3
import sys
import json
import os
import time

# Simulación de AGI para lógica de decisión
# Este script se ejecuta desde Asterisk para decidir si usar el Mensaje Fijo o llamar a Víctor (IA)

STATE_FILE = '/root/ai_bridge/current_mode.json' # Ruta en el VPS

def get_current_mode():
    try:
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, 'r') as f:
                return json.load(f).get('mode', 'AI')
    except Exception:
        pass
    return 'AI'

def agi_log(message):
    sys.stderr.write(f"VICTOR_DECISION: {message}\n")
    sys.stderr.flush()

def main():
    mode = get_current_mode()
    agi_log(f"Modo detectado: {mode}")
    
    if mode == 'FIXED':
        # Ordenar a Asterisk que reproduzca el mensaje fijo y cuelgue
        print("EXEC Playback /var/lib/asterisk/sounds/es/fixed_spam_message")
        sys.stdout.flush()
        res = sys.stdin.readline()
        
        print("EXEC Hangup")
        sys.stdout.flush()
        res = sys.stdin.readline()
    else:
        # Proceder con la IA Víctor (Llamar al script de IA existente)
        # Esto es solo un puente, la lógica real de IA está en victor_agi.py original
        print("EXEC AGI /var/lib/asterisk/agi-bin/victor_agi.py")
        sys.stdout.flush()
        res = sys.stdin.readline()

if __name__ == "__main__":
    main()
