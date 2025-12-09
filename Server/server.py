import os
from flask import Flask, jsonify, request
import pyautogui
import time

app = Flask(__name__)

# --- CONFIGURACIÓN ---
# Pon la ruta de tu carpeta aquí. Usa r"" para evitar problemas con las barras \
# Ejemplo: r"C:\Users\TuUsuario\Documents\MisExpos"
CARPETA_PRESENTACIONES = r"C:\Users\baner\OneDrive\Documentos\Presentaciones" 

# Configuramos pyautogui para que no sea tan lento por seguridad
pyautogui.PAUSE = 0.1

# --- RUTA 1: OBTENER LISTA DE ARCHIVOS ---
@app.route('/files', methods=['GET'])
def list_files():
    try:
        archivos = []
        # Buscamos archivos .pptx o .ppt en la carpeta
        for archivo in os.listdir(CARPETA_PRESENTACIONES):
            if archivo.endswith((".pptx", ".ppt")):
                archivos.append(archivo)
        return jsonify(archivos) # Retorna JSON: ["Expo1.pptx", "Tesis.pptx"]
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# --- RUTA 2: ABRIR ARCHIVO ---
@app.route('/open', methods=['POST'])
def open_file():
    nombre_archivo = request.args.get('name') # Recibimos el nombre por URL
    if not nombre_archivo:
        return "Falta el nombre", 400
    
    ruta_completa = os.path.join(CARPETA_PRESENTACIONES, nombre_archivo)

    if os.path.exists(ruta_completa):
        os.startfile(ruta_completa)
        
        # Le damos 6 segundos a PowerPoint para que arranque (ajusta si tu PC es más lenta/rápida)
        time.sleep(6) 
        pyautogui.press('f5') # <--- El toque mágico
        
        return "Abierto", 200
    
    if os.path.exists(ruta_completa):
        os.startfile(ruta_completa) # Comando nativo de Windows para abrir archivo
        # Opcional: Esperar un poco y presionar F5 para iniciar presentación
        # pyautogui.sleep(2)
        # pyautogui.press('f5')
        return "Abierto", 200
    return "Archivo no encontrado", 404

# --- RUTA 3: CONTROL BÁSICO (Siguiente, Atras, Blackout) ---
@app.route('/control/<action>', methods=['POST'])
def control_slide(action):
    print(f"Comando recibido: {action}")
    if action == 'next':
        pyautogui.press('right')
    elif action == 'prev':
        pyautogui.press('left')
    elif action == 'blackout':
        pyautogui.press('b') # Pone la pantalla en negro
    elif action == 'stop':
        pyautogui.press('esc') # Salir de la presentación
    elif action == 'close':
        print("Cerrando PowerPoint...")
        pyautogui.hotkey('alt', 'f4')   # Opción 1: La forma educada (Alt + F4)
    elif action == 'resume':
        pyautogui.hotkey('shift', 'f5') # Shift + F5 reanuda la presentación en la diapositiva actual    
    return "OK", 200
    

# --- RUTA 4: MODO CONSOLA (D-Pad y Enter) ---
@app.route('/key/<key_name>', methods=['POST'])
def key_press(key_name):
    teclas_validas = ['up', 'down', 'left', 'right', 'enter', 'tab', 'space']
    if key_name in teclas_validas:
        pyautogui.press(key_name)
        return "OK", 200
    return "Tecla invalida", 400

# --- RUTA 5: SALTO NUMÉRICO ---
@app.route('/jump/<number>', methods=['POST'])
def jump_slide(number):
    if number.isdigit():
        # PowerPoint truco: Escribir numero + Enter
        pyautogui.typewrite(number)
        pyautogui.press('enter')
        return f"Saltando a {number}", 200
    return "No es numero", 400

if __name__ == '__main__':
    # host='0.0.0.0' permite que otros dispositivos en la red se conecten
    print("--- SERVIDOR LISTO ---")
    print("Averigua tu IP con 'ipconfig' en otra terminal")
    app.run(host='0.0.0.0', port=5000)