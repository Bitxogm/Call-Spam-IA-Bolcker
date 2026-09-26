from flask import Flask, request, jsonify
import json
import os
import hmac
from functools import wraps

app = Flask(__name__)
STATE_FILE = 'current_mode.json'
CALL_LOG_FILE = '/root/ai_bridge/call_log.json'   # absoluta: la escribe manolo_ari.py

API_TOKEN = os.getenv('CONTROL_API_TOKEN')

# Nginx Proxy Manager corre en un contenedor: para él 127.0.0.1 es el propio
# contenedor, no el host. El valor real es el gateway del bridge Docker al que
# NPM está conectado. Por defecto loopback: si la variable falta, el servicio
# queda inalcanzable (fallo visible) en vez de escuchando donde no toca.
BIND_HOST = os.getenv('CONTROL_API_BIND', '127.0.0.1')


def requiere_token(f):
    """
    Exige la cabecera X-API-Key en todos los endpoints.

    Sin CONTROL_API_TOKEN configurado se rechaza TODO: falla cerrado. Lo
    contrario -sin token, permitir- es como estos servicios acaban abiertos
    tras un despliegue en el que se olvidó el .env.
    """
    @wraps(f)
    def wrapper(*args, **kwargs):
        enviado = request.headers.get('X-API-Key', '')
        # compare_digest en vez de ==: comparación en tiempo constante
        if not API_TOKEN or not hmac.compare_digest(enviado, API_TOKEN):
            return jsonify({'status': 'error', 'message': 'unauthorized'}), 401
        return f(*args, **kwargs)
    return wrapper

def load_call_log():
    try:
        with open(CALL_LOG_FILE, 'r') as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except (FileNotFoundError, json.JSONDecodeError):
        return []

def save_mode(mode):
    with open(STATE_FILE, 'w') as f:
        json.dump({'mode': mode}, f)

def load_mode():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, 'r') as f:
            return json.load(f).get('mode', 'AI')
    return 'AI'

@app.route('/set_mode', methods=['POST'])
@requiere_token
def set_mode():
    data = request.json
    if not data:
        return jsonify({'status': 'error', 'message': 'No JSON data'}), 400
        
    mode = data.get('mode')
    if mode in ['FIXED', 'AI']:
        save_mode(mode)
        print(f"Modo cambiado a: {mode}")
        return jsonify({'status': 'success', 'mode': mode}), 200
    return jsonify({'status': 'error', 'message': 'Invalid mode'}), 400

@app.route('/get_mode', methods=['GET'])
@requiere_token
def get_mode():
    mode = load_mode()
    return jsonify({'mode': mode}), 200

@app.route('/call_log', methods=['GET'])
@requiere_token
def call_log():
    registros = load_call_log()
    # timestamp_inicio es ISO 8601: ordenar como string equivale a ordenar por fecha
    registros.sort(key=lambda r: r.get('timestamp_inicio') or '', reverse=True)
    return jsonify(registros[:50]), 200


if __name__ == '__main__':
    # Inicializar con AI por defecto
    if not os.path.exists(STATE_FILE):
        save_mode('AI')
    print("========================================")
    print("🚀 API de Control Manolo")
    print(f"📡 Escuchando en: http://{BIND_HOST}:5000")
    print(f"🔑 Token: {'configurado' if API_TOKEN else '❌ NO CONFIGURADO — todo devolverá 401'}")
    print("========================================")
    # threaded=True permite que varias peticiones no se bloqueen entre sí
    app.run(host=BIND_HOST, port=5000, threaded=True, debug=False)
