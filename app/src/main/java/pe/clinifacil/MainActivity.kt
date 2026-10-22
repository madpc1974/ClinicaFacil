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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Clinica(val nombre: String, val detalle: String, val lat: Double, val lon: Double, val km: Float)

class MainActivity : AppCompatActivity() {
    // etiqueta -> (valor OSM healthcare:speciality, palabras clave en el nombre)
    private val especialidades = linkedMapOf(
        "Todas las especialidades" to Pair("", listOf<String>()),
        "Medicina general" to Pair("general", listOf("general", "familiar", "medicina")),
        "Pediatría" to Pair("paediatrics", listOf("pediat", "niño")),
        "Ginecología" to Pair("gynaecology", listOf("gineco", "obstet", "materno")),
        "Cardiología" to Pair("cardiology", listOf("cardio", "corazón")),
        "Dermatología" to Pair("dermatology", listOf("dermat", "piel")),
        "Traumatología" to Pair("orthopaedics", listOf("trauma", "ortoped")),
        "Oftalmología" to Pair("ophthalmology", listOf("oftalm", "ojos", "visión")),
        "Odontología" to Pair("dentist", listOf("odonto", "dental", "dentista")),
        "Psicología / Psiquiatría" to Pair("psychiatry", listOf("psico", "psiqui", "mental")),
        "Urología" to Pair("urology", listOf("urolog")),
        "Oncología" to Pair("oncology", listOf("oncol", "cáncer", "neoplás")),
        "Laboratorio / Imágenes" to Pair("radiology", listOf("laborat", "imágenes", "radiolog", "diagnóst"))
    )
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
        spEsp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, especialidades.keys.toList())
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
        val (osm, claves) = especialidades[spEsp.selectedItem as String]!!
        val r = radioKm() * 1000
        val q = "[out:json][timeout:25];nwr[\"amenity\"~\"clinic|hospital|doctors|dentist\"](around:$r,${loc.latitude},${loc.longitude});out center 150;"
        Thread {
            try {
                val c = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 30000
                c.outputStream.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray())
                val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("elements")
                val out = mutableListOf<Clinica>()
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i); val t = e.optJSONObject("tags") ?: continue
                    val lat = e.optDouble("lat", e.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
                    val lon = e.optDouble("lon", e.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val nombre = t.optString("name", "Centro de salud")
                    val esp = t.optString("healthcare:speciality", "")
                    if (osm.isNotEmpty() && !(esp.contains(osm) || claves.any { nombre.lowercase().contains(it) })) continue
                    val d = FloatArray(1); Location.distanceBetween(loc.latitude, loc.longitude, lat, lon, d)
                    val dir = listOf(t.optString("addr:street"), t.optString("addr:housenumber")).filter { it.isNotEmpty() }.joinToString(" ")
                    out.add(Clinica(nombre, dir, lat, lon, d[0] / 1000f))
                }
                out.sortBy { it.km }
                runOnUiThread { mostrar(out) }
            } catch (ex: Exception) {
                runOnUiThread { estado.text = "No se pudo consultar. Revisa tu conexión a internet." }
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
