import os
import socket
import threading
import time  
import ctypes
from ctypes import wintypes
import qrcode
import tkinter as tk
from PIL import Image, ImageTk
from flask import Flask, jsonify, request
import pyautogui

# --- CONFIGURACIÓN DE LA APP ---
app_flask = Flask(__name__)
PORT = 5000
pyautogui.PAUSE = 0.1

# --- FUNCIÓN PARA OBTENER EL ESCRITORIO REAL (OneDrive Proof) ---
def get_real_desktop_path():
    CSIDL_DESKTOPDIRECTORY = 0x0010
    buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
    ctypes.windll.shell32.SHGetFolderPathW(None, CSIDL_DESKTOPDIRECTORY, None, 0, buf)
    return buf.value

# 1. CREAR CARPETA EN EL ESCRITORIO
desktop_path = get_real_desktop_path()
CARPETA_PRESENTACIONES = os.path.join(desktop_path, "DROP_PRESENTACIONES_AQUI")

if not os.path.exists(CARPETA_PRESENTACIONES):
    os.makedirs(CARPETA_PRESENTACIONES)

# --- RUTAS DE FLASK ---

# --- RUTA MEJORADA: BUSCAR EN SUBCARPETAS ---
@app_flask.route('/files', methods=['GET'])
def list_files():
    try:
        archivos = []
        # os.walk recorre el árbol de directorios completo (recursivo)
        for root, dirs, files in os.walk(CARPETA_PRESENTACIONES):
            for file in files:
                if file.endswith((".pptx", ".ppt")):
                    # Calculamos la ruta relativa para que se vea: "Carpeta\Archivo.pptx"
                    # Si está en la raíz, root es igual a CARPETA_PRESENTACIONES
                    rel_dir = os.path.relpath(root, CARPETA_PRESENTACIONES)
                    
                    if rel_dir == ".":
                        # Está suelto en la carpeta principal
                        archivos.append(file)
                    else:
                        # Está dentro de una subcarpeta
                        # Unimos carpeta + nombre (ej: "Español\Expo.pptx")
                        ruta_relativa = os.path.join(rel_dir, file)
                        archivos.append(ruta_relativa)
                        
        return jsonify(archivos)
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    
@app_flask.route('/open', methods=['POST'])
def open_file():
    nombre = request.args.get('name')
    if nombre:
        ruta = os.path.join(CARPETA_PRESENTACIONES, nombre)
        if os.path.exists(ruta):
            os.startfile(ruta)
            
            # --- AUTO-START (Esperar y presionar F5) ---
            
            time.sleep(5) 
            pyautogui.press('f5') 
            
            return "Abierto", 200
    return "Error", 404

@app_flask.route('/control/<action>', methods=['POST'])
def control(action):
    print(f"Accion: {action}")
    if action == 'next': pyautogui.press('right')
    elif action == 'prev': pyautogui.press('left')
    elif action == 'blackout': pyautogui.press('b')
    elif action == 'stop': pyautogui.press('esc')
    elif action == 'close': pyautogui.hotkey('alt', 'f4')
    
    # --- FIX: BOTÓN PLAY / RESUME ---
    elif action == 'resume': pyautogui.hotkey('shift', 'f5')
    
    return "OK", 200

# --- EXTRAS: NUMPAD Y CONSOLA (Agregados de vuelta) ---
@app_flask.route('/jump/<number>', methods=['POST'])
def jump_slide(number):
    if number.isdigit():
        pyautogui.typewrite(number)
        pyautogui.press('enter')
        return "Saltando", 200
    return "Error", 400

@app_flask.route('/key/<key_name>', methods=['POST'])
def key_press(key_name):
    teclas = ['up', 'down', 'left', 'right', 'enter', 'tab', 'space']
    if key_name in teclas:
        pyautogui.press(key_name)
        return "OK", 200
    return "Error", 400

# --- LÓGICA DE RED ---
def obtener_ip_local():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

# --- INTERFAZ GRÁFICA ---
def iniciar_gui(ip_address):
    root = tk.Tk()
    root.title("Servidor Office Remote")
    root.geometry("400x500")
    root.configure(bg="white")

    lbl_info = tk.Label(root, text="Escanea para conectar:", font=("Arial", 14), bg="white")
    lbl_info.pack(pady=10)

    # Generar QR
    url_conectar = f"http://{ip_address}:{PORT}"
    qr = qrcode.QRCode(box_size=10, border=2)
    qr.add_data(url_conectar)
    qr.make(fit=True)
    img_qr = qr.make_image(fill="black", back_color="white")
    img_tk = ImageTk.PhotoImage(img_qr)

    lbl_img = tk.Label(root, image=img_tk, bg="white")
    lbl_img.image = img_tk
    lbl_img.pack(pady=10)

    lbl_ip = tk.Label(root, text=f"IP: {ip_address}", font=("Arial", 12, "bold"), fg="#333", bg="white")
    lbl_ip.pack(pady=5)
    
    # Texto de la carpeta actualizado
    lbl_folder = tk.Label(root, text=f"CARPETA EN EL ESCRITORIO:\n'DROP_PRESENTACIONES_AQUI'", 
                          font=("Arial", 10, "bold"), fg="#2E7D32", bg="white")
    lbl_folder.pack(pady=15, side=tk.BOTTOM)

    root.protocol("WM_DELETE_WINDOW", lambda: os._exit(0))
    root.mainloop()

if __name__ == '__main__':
    ip = obtener_ip_local()
    hilo_server = threading.Thread(target=lambda: app_flask.run(host='0.0.0.0', port=PORT, use_reloader=False))
    hilo_server.daemon = True
    hilo_server.start()
    print(f"Servidor corriendo en {ip}:{PORT}")
    iniciar_gui(ip)