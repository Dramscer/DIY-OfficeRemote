package com.tuapp.control

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // Importante
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.journeyapps.barcodescanner.ScanContract // Importante
import com.journeyapps.barcodescanner.ScanOptions // Importante
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var recycler: RecyclerView
    private val client = OkHttpClient()

    // --- 1. PREPARAMOS EL LANZADOR DE LA CÁMARA ---
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // El QR contiene la URL completa: "http://192.168.1.50:5000"
            val urlCompleta = result.contents
            Toast.makeText(this, "Conectando a: $urlCompleta", Toast.LENGTH_SHORT).show()

            // Extraemos solo la IP para usarla en nuestra lógica vieja, o usamos la URL directa
            // Truco rápido: Parseamos la IP del string
            try {
                // Quitamos "http://" y ":5000" para llenar el campo de texto visualmente
                val ipLimpia = urlCompleta.replace("http://", "").replace(":5000", "")
                etIp.setText(ipLimpia)

                // Ejecutamos la búsqueda inmediatamente
                cargarArchivos(ipLimpia)
            } catch (e: Exception) {
                Toast.makeText(this, "QR Inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etIp)
        recycler = findViewById(R.id.recyclerArchivos)
        recycler.layoutManager = LinearLayoutManager(this)

        // Botón Manual
        findViewById<Button>(R.id.btnConectar).setOnClickListener {
            val ip = etIp.text.toString()
            cargarArchivos(ip)
        }

        // --- 2. BOTÓN DE ESCANEAR ---
        findViewById<Button>(R.id.btnScanQR).setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Escanea el QR de tu PC")
            options.setCameraId(0) // Usar cámara trasera
            options.setBeepEnabled(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
        }
    }

    private fun cargarArchivos(ip: String) {
        // ... tu código anterior ...
        val url = "http://$ip:5000/files"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al conectar", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val tipoLista = object : TypeToken<List<String>>() {}.type
                    val listaArchivos: List<String> = Gson().fromJson(json, tipoLista)

                    runOnUiThread {
                        recycler.adapter = ArchivosAdapter(listaArchivos) { archivoSeleccionado ->
                            abrirPresentacion(ip, archivoSeleccionado)
                        }
                    }
                }
            }
        })
    }

    private fun abrirPresentacion(ip: String, nombreArchivo: String) {
        // ... tu código anterior para abrir y cambiar de activity ...
        val url = "http://$ip:5000/open?name=$nombreArchivo"
        val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        val intent = Intent(this@MainActivity, ControlActivity::class.java)
                        intent.putExtra("IP_SERVIDOR", ip)
                        startActivity(intent)
                    }
                }
            }
        })
    }

    // ... Tu class ArchivosAdapter sigue igual ...
    class ArchivosAdapter(
        private val lista: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ArchivosAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val texto: android.widget.TextView = view.findViewById(R.id.txtNombreArchivo)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_archivo, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val archivo = lista[position]
            holder.texto.text = archivo
            holder.itemView.setOnClickListener { onClick(archivo) }
        }

        override fun getItemCount() = lista.size
    }
}