package com.tuapp.control

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var recycler: RecyclerView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etIp)
        recycler = findViewById(R.id.recyclerArchivos)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnConectar).setOnClickListener {
            val ip = etIp.text.toString()
            cargarArchivos(ip)
        }
    }

    private fun cargarArchivos(ip: String) {
        val url = "http://$ip:5000/files"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    // Convertimos JSON a Lista de Strings usando Gson
                    val tipoLista = object : TypeToken<List<String>>() {}.type
                    val listaArchivos: List<String> = Gson().fromJson(json, tipoLista)

                    runOnUiThread {
                        // Asignamos el adaptador con la lista recibida
                        recycler.adapter = ArchivosAdapter(listaArchivos) { archivoSeleccionado ->
                            abrirPresentacion(ip, archivoSeleccionado)
                        }
                    }
                }
            }
        })
    }

    private fun abrirPresentacion(ip: String, nombreArchivo: String) {
        val url = "http://$ip:5000/open?name=$nombreArchivo"
        // Request POST vacío, solo para activar la orden
        val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Abriendo $nombreArchivo...", Toast.LENGTH_SHORT).show()

                        // AQUÍ ES DONDE NAVEGAREMOS A LA PANTALLA 2 (CONTROL)
                         val intent = Intent(this@MainActivity, ControlActivity::class.java)
                        intent.putExtra("IP_SERVIDOR", ip)
                        startActivity(intent)
                    }
                }
            }
        })
    }

    // --- ADAPTER INTERNO (Para no crear otro archivo) ---
    class ArchivosAdapter(
        private val lista: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ArchivosAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val texto: TextView = view.findViewById(R.id.txtNombreArchivo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_archivo, parent, false)
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