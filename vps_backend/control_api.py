from flask import Flask, request, jsonify
import json
import os

app = Flask(__name__)
STATE_FILE = 'current_mode.json'

def save_mode(mode):
    with open(STATE_FILE, 'w') as f:
        json.dump({'mode': mode}, f)

def load_mode():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, 'r') as f:
            return json.load(f).get('mode', 'AI')
    return 'AI'

@app.route('/set_mode', methods=['POST'])
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
def get_mode():
    mode = load_mode()
    return jsonify({'mode': mode}), 200

if __name__ == '__main__':
    # Inicializar con AI por defecto
    if not os.path.exists(STATE_FILE):
        save_mode('AI')
    print("API de Control Víctor iniciada en el puerto 5000...")
    app.run(host='0.0.0.0', port=5000)
