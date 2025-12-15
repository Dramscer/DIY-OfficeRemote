package com.tuapp.control

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ControlActivity : AppCompatActivity() {

    private lateinit var tvCronometro: TextView
    private val client = OkHttpClient()
    private var ipServer: String = ""
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        ocultarBarras()

        // 1. Recibir IP
        ipServer = intent.getStringExtra("IP_SERVIDOR") ?: ""
        tvCronometro = findViewById(R.id.tvCronometro)

        // 2. Configurar Botones
        findViewById<Button>(R.id.btnNext).setOnClickListener { enviarComando("next") }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { enviarComando("prev") }
        findViewById<Button>(R.id.btnBlackout).setOnClickListener { enviarComando("blackout") }
        // Botón "IR A..." (NumPad)
        findViewById<Button>(R.id.btnNumPad).setOnClickListener {
            mostrarDialogoIrA()
        }

        // Botón "CONSOLA" (DPad)
        findViewById<Button>(R.id.btnDPad).setOnClickListener {
            mostrarConsola()
        }
        findViewById<Button>(R.id.btnResume).setOnClickListener {
            enviarComando("resume")
            // Opcional: Volver a ocultar barras por si aparecieron
            ocultarBarras()
        }
        // 1. Click Normal (Toque corto) -> Solo sale de la presentación y cierra la app del cel
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            enviarComando("stop")
            finish()
        }
// 2. Click Largo (Dejar presionado) -> Cierra PowerPoint en la PC
        findViewById<Button>(R.id.btnStop).setOnLongClickListener {
            enviarComando("close")

            // Feedback para que sepas que funcionó
            Toast.makeText(this, "Cerrando PowerPoint...", Toast.LENGTH_SHORT).show()
            vibrar(100) // Una vibración cortita para confirmar

            finish() // Cierra la app del cel
            true // 'true' indica que ya manejamos el evento y no debe ejecutarse el click normal después
        }

        // 3. Iniciar el diálogo del tiempo
        mostrarDialogoTiempo()
    }





    // --- FUNCIÓN: IR A DIAPOSITIVA ESPECÍFICA ---
    private fun mostrarDialogoIrA() {
        val input = EditText(this)
        input.hint = "Número de diapositiva"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER

        AlertDialog.Builder(this)
            .setTitle("Saltar a diapositiva")
            .setView(input)
            .setPositiveButton("IR") { _, _ ->
                val numero = input.text.toString()
                if (numero.isNotEmpty()) {
                    // Usamos una ruta especial para saltar
                    enviarPeticion("/jump/$numero")
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    // --- FUNCIÓN: MOSTRAR CONSOLA FLOTANTE ---
    private fun mostrarConsola() {
        val view = layoutInflater.inflate(R.layout.dialog_dpad, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setTitle("Control D-Pad")
            .create()

        // Conectamos los botones del XML a comandos
        view.findViewById<Button>(R.id.btnUp).setOnClickListener { enviarPeticion("/key/up") }
        view.findViewById<Button>(R.id.btnDown).setOnClickListener { enviarPeticion("/key/down") }
        view.findViewById<Button>(R.id.btnLeft).setOnClickListener { enviarPeticion("/key/left") }
        view.findViewById<Button>(R.id.btnRight).setOnClickListener { enviarPeticion("/key/right") }
        view.findViewById<Button>(R.id.btnEnter).setOnClickListener { enviarPeticion("/key/enter") }
        view.findViewById<Button>(R.id.btnTab).setOnClickListener { enviarPeticion("/key/tab") }

        dialog.show()
    }

    private fun enviarPeticion(rutaRelativa: String) {
        if (ipServer.isEmpty()) return

        // Si la ruta ya trae "/", no se lo ponemos. Si no, se lo agregamos.
        // Ejemplo: rutaRelativa puede ser "control/next" o "/jump/5"
        val path = if (rutaRelativa.startsWith("/")) rutaRelativa else "/control/$rutaRelativa"

        val url = "http://$ipServer:5000$path"
        val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    // --- FUNCIÓN DE DIÁLOGO PARA EL TIEMPO ---
    private fun mostrarDialogoTiempo() {
        val input = EditText(this)
        input.hint = "Minutos (Ej: 15)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Tiempo de Exposición")
            .setMessage("¿Cuántos minutos tienes?")
            .setView(input)
            .setCancelable(false) // Obligatorio poner tiempo
            .setPositiveButton("INICIAR") { _, _ ->
                val minutos = input.text.toString().toLongOrNull() ?: 10 // Default 10 min
                iniciarCuentaRegresiva(minutos * 60 * 1000)
            }
            .setNegativeButton("SIN TIEMPO") { _, _ ->
                tvCronometro.text = "--:--"
            }
            .show()
    }

    // --- LÓGICA DEL CRONÓMETRO Y VIBRACIÓN ---
    private fun iniciarCuentaRegresiva(milisegundos: Long) {
        timer = object : CountDownTimer(milisegundos, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Formato MM:SS
                val minutos = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val segundos = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvCronometro.text = String.format("%02d:%02d", minutos, segundos)

                // ALERTA 1: Faltan 2 minutos (entre 119s y 120s)
                if (millisUntilFinished in 119000..120000) {
                    vibrar(500) // Vibración corta
                    Toast.makeText(applicationContext, "¡Quedan 2 minutos!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFinish() {
                tvCronometro.text = "00:00"
                vibrar(longArrayOf(0, 500, 200, 500, 200, 500)) // 3 vibraciones fuertes
            }
        }.start()
    }

    // --- FUNCIÓN PARA VIBRAR (Compatible con Android nuevo y viejo) ---
    private fun vibrar(duracion: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Usamos la forma simple deprecated para compatibilidad rápida o VibrationEffect en nuevos
        // Por simplicidad en este ejemplo rápido:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duracion, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duracion)
        }
    }

    // Sobrecarga para patrones de vibración
    private fun vibrar(patron: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(patron, -1))
        } else {
            vibrator.vibrate(patron, -1)
        }
    }

    // --- BOTONES DE VOLUMEN ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                enviarComando("next")
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                enviarComando("prev")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // --- RED ---
    private fun enviarComando(accion: String) {
        if (ipServer.isEmpty()) return
        val url = "http://$ipServer:5000/control/$accion"
        val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel() // Cancelar timer si salimos para no gastar recursos
    }
    private fun ocultarBarras() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private fun ControlActivity.enviarPeticion(string: String) {}
