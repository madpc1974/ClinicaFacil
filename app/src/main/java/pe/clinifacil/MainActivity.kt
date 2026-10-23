package pe.clinifacil

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private lateinit var lista: ListView
    private lateinit var estado: TextView
    private lateinit var spEsp: Spinner
    private lateinit var sbRadio: SeekBar
    private var items = listOf<Clinica>()

    private fun radioKm() = sbRadio.progress + 1

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        lista = findViewById(R.id.lista); estado = findViewById(R.id.tvEstado)
        spEsp = findViewById(R.id.spEsp); sbRadio = findViewById(R.id.sbRadio)
        val tvRadio = findViewById<TextView>(R.id.tvRadio)
        spEsp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Overpass.ESPECIALIDADES.keys.toList())
        tvRadio.text = "Radio de búsqueda: ${radioKm()} km"
        sbRadio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, u: Boolean) { tvRadio.text = "Radio de búsqueda: ${radioKm()} km" }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
        })
        findViewById<Button>(R.id.btnBuscar).setOnClickListener { buscar() }
        lista.setOnItemClickListener { _, _, i, _ ->
            val c = items[i]
            val uri = Uri.parse("geo:${c.lat},${c.lon}?q=${c.lat},${c.lon}(${Uri.encode(c.nombre)})")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (g.any { it == PackageManager.PERMISSION_GRANTED }) buscar()
        else estado.text = "Se necesita el permiso de ubicación para buscar cerca de ti."
    }

    @SuppressLint("MissingPermission")
    private fun buscar() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1); return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provs = (if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                     else listOf(LocationManager.NETWORK_PROVIDER)).filter { lm.isProviderEnabled(it) }
        if (provs.isEmpty()) { estado.text = "Activa la ubicación del teléfono e inténtalo de nuevo."; return }
        estado.text = "Obteniendo tu ubicación…"
        val last = provs.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 120_000) { consultar(last); return }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val listeners = mutableListOf<LocationListener>()
        var terminado = false
        fun terminar(l: Location?) {
            if (terminado) return
            terminado = true
            listeners.forEach { lm.removeUpdates(it) }
            handler.removeCallbacksAndMessages(null)
            if (l != null) consultar(l)
            else estado.text = "No se pudo obtener tu ubicación. Acércate a una ventana o sal a un lugar abierto e inténtalo de nuevo."
        }
        provs.forEach { pr ->
            val li = object : LocationListener {
                override fun onLocationChanged(l: Location) { terminar(l) }
                @Deprecated("") override fun onStatusChanged(p: String?, st: Int, e: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            listeners.add(li)
            lm.requestLocationUpdates(pr, 0L, 0f, li)
        }
        handler.postDelayed({ terminar(last) }, 20_000)
    }

    private fun consultar(loc: Location) {
        runOnUiThread { estado.text = "Buscando clínicas…" }
        val esp = spEsp.selectedItem as String
        val km = radioKm()
        Thread {
            try {
                val r = Overpass.buscar(loc.latitude, loc.longitude, km, esp) { msg -> runOnUiThread { estado.text = msg } }
                runOnUiThread { mostrar(r) }
            } catch (ex: Exception) {
                runOnUiThread { estado.text = "No se pudo consultar (${ex.message}). Inténtalo de nuevo en unos segundos." }
            }
        }.start()
    }

    private fun mostrar(r: List<Clinica>) {
        items = r
        estado.text = if (r.isEmpty()) "Sin resultados. Prueba con un radio mayor u otra especialidad." else "${r.size} resultado(s). Toca uno para ir."
        lista.adapter = object : ArrayAdapter<Clinica>(this, android.R.layout.simple_list_item_2, android.R.id.text1, r) {
            override fun getView(p: Int, v: View?, g: ViewGroup): View {
                val row = super.getView(p, v, g); val c = r[p]
                row.findViewById<TextView>(android.R.id.text1).apply { text = c.nombre; setTextColor(Color.parseColor("#0B1F4B")); setTypeface(null, 1) }
                row.findViewById<TextView>(android.R.id.text2).apply {
                    text = String.format("%.1f km%s", c.km, if (c.detalle.isNotEmpty()) "  ·  ${c.detalle}" else ""); setTextColor(Color.DKGRAY)
                }
                return row
            }
        }
    }
}
