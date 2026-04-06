#!/root/ai_bridge/venv/bin/python3
import os
import sys
import socket
from datetime import datetime

sys.path.insert(0, '/root/ai_bridge/venv/lib/python3.12/site-packages')
sys.path.insert(0, '/root/ai_bridge')

import whisper

SOCKET_PATH = '/tmp/whisper.sock'
MODEL_NAME = 'small'

def log(message):
    ts = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    sys.stderr.write(f'[{ts}] WHISPER_SERVER: {message}\n')
    sys.stderr.flush()

def transcribe(model, wav_path):
    result = model.transcribe(wav_path, language='es')
    return result.get('text', '').strip()

def handle_connection(conn, model):
    try:
        data = b''
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            data += chunk
            if b'\n' in data:
                break

        wav_path = data.decode('utf-8', errors='replace').strip()
        if not wav_path:
            conn.sendall(b'\n')
            log('Ruta vacía recibida')
            return

        if not os.path.exists(wav_path):
            conn.sendall(b'\n')
            log(f'No existe el archivo: {wav_path}')
            return

        text = transcribe(model, wav_path)
        conn.sendall((text + '\n').encode('utf-8'))
        log(f"Transcrito OK: {wav_path} -> '{text}'")
    except Exception as e:
        try:
            conn.sendall(b'\n')
        except Exception:
            pass
        log(f'Error manejando conexión: {e}')
    finally:
        conn.close()

def main():
    if os.path.exists(SOCKET_PATH):
        os.remove(SOCKET_PATH)

    log(f'Cargando Whisper {MODEL_NAME}...')
    model = whisper.load_model(MODEL_NAME)
    log(f'Whisper {MODEL_NAME} cargado en memoria')

    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(SOCKET_PATH)
    os.chmod(SOCKET_PATH, 0o666)
    server.listen(16)
    log(f'Escuchando en socket Unix {SOCKET_PATH}')

    try:
        while True:
            conn, _ = server.accept()
            handle_connection(conn, model)
    except KeyboardInterrupt:
        log('Interrupción recibida, cerrando servidor')
    finally:
        server.close()
        if os.path.exists(SOCKET_PATH):
            os.remove(SOCKET_PATH)
        log('Servidor detenido')

if __name__ == '__main__':
    main()
