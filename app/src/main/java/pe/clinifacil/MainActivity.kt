package pe.clinifacil

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog


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
        lista.setOnItemClickListener { _, _, i, _ -> mostrarDetalle(items[i]) }
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fila(cont: LinearLayout, titulo: String, valor: String?) {
        if (valor.isNullOrBlank()) return
        cont.addView(TextView(this).apply {
            text = titulo; textSize = 12f; setTextColor(Color.parseColor("#0B1F4B")); setTypeface(null, Typeface.BOLD); setPadding(0, dp(12), 0, 0)
        })
        cont.addView(TextView(this).apply { text = valor; textSize = 15f; setTextColor(Color.parseColor("#1F2937")) })
    }

    private fun abrir(uri: String, alt: String? = null) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: ActivityNotFoundException) {
            try {
                if (alt != null) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alt)))
                else Toast.makeText(this, "No hay una app para abrir esto", Toast.LENGTH_SHORT).show()
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, "No hay una app para abrir esto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarDetalle(c: Clinica) {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.dialog_detalle, null)
        v.findViewById<TextView>(R.id.tvNom).text = c.nombre
        v.findViewById<TextView>(R.id.tvSub).text = Detalle.tipo(c.tags) + "  ·  " + String.format("%.1f km de ti", c.km)
        val info = v.findViewById<LinearLayout>(R.id.llInfo)
        fila(info, "Dirección", Detalle.direccion(c.tags))
        fila(info, "Horario", Detalle.horario(c.tags["opening_hours"]))
        fila(info, "Especialidades", Detalle.especialidades(c.tags, c.nombre))
        fila(info, "Teléfono", Detalle.telefono(c.tags))
        fila(info, "Sitio web", Detalle.web(c.tags))
        Detalle.extras(c.tags).forEach { fila(info, it.first, it.second) }
        v.findViewById<TextView>(R.id.tvPrecio).text =
            "Precios: las fuentes públicas no los incluyen. Llama a la clínica o busca sus tarifas en Google."
        v.findViewById<Button>(R.id.btnRuta).setOnClickListener {
            abrir("google.navigation:q=${c.lat},${c.lon}", "geo:${c.lat},${c.lon}?q=${c.lat},${c.lon}(${Uri.encode(c.nombre)})")
        }
        val tel = Detalle.telefonoMarcable(c.tags)
        v.findViewById<Button>(R.id.btnLlamar).apply {
            visibility = if (tel == null) View.GONE else View.VISIBLE
            setOnClickListener { abrir("tel:$tel") }
        }
        val web = Detalle.web(c.tags)
        v.findViewById<Button>(R.id.btnWeb).apply {
            visibility = if (web == null) View.GONE else View.VISIBLE
            setOnClickListener { abrir(web ?: "") }
        }
        v.findViewById<Button>(R.id.btnTarifas).setOnClickListener {
            abrir("https://www.google.com/search?q=" + Uri.encode("${c.nombre} precio consulta"))
        }
        d.setContentView(v)
        d.show()
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
