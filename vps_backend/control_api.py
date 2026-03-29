from flask import Flask, request, jsonify
import json
import os
from gtts import gTTS
import subprocess

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

@app.route('/set_message', methods=['POST'])
def set_message():
    data = request.json
    if not data or 'text' not in data:
        return jsonify({'status': 'error', 'message': 'No text provided'}), 400
        
    text = data.get('text')
    try:
        # Generar audio con gTTS
        tts = gTTS(text=text, lang='es')
        temp_mp3 = "custom_fixed.mp3"
        target_wav = "custom_fixed_message.wav"
        
        tts.save(temp_mp3)
        
        # Convertir a formato WAV legible por Asterisk (8000Hz, mono, pcm_s16le)
        # Usamos ffmpeg para asegurar compatibilidad total
        subprocess.run([
            'ffmpeg', '-y', '-i', temp_mp3, 
            '-ar', '8000', '-ac', '1', 
            '-codec:a', 'pcm_s16le', 
            target_wav
        ], check=True)
        
        # Limpiar temporal
        if os.path.exists(temp_mp3):
            os.remove(temp_mp3)
            
        print(f"✅ Nuevo audio corporativo generado: {text[:30]}...")
        return jsonify({'status': 'success', 'message': 'Audio generated'}), 200
    except Exception as e:
        print(f"❌ Error generando audio: {str(e)}")
        return jsonify({'status': 'error', 'message': str(e)}), 500

if __name__ == '__main__':
    # Inicializar con AI por defecto
    if not os.path.exists(STATE_FILE):
        save_mode('AI')
    print("========================================")
    print("🚀 API de Control Víctor (MODO SEGURO)")
    print("📡 Escuchando en: http://0.0.0.0:5000")
    print("========================================")
    # threaded=True permite que múltiples rquest no bloqueen el servidor
    app.run(host='0.0.0.0', port=5000, threaded=True, debug=False)
