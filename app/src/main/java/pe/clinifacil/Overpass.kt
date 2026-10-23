package pe.clinifacil

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Clinica(val nombre: String, val detalle: String, val lat: Double, val lon: Double, val km: Float, val tags: Map<String, String> = emptyMap())

object Overpass {
    val ESPECIALIDADES = linkedMapOf(
        "Todas las especialidades" to Pair("", listOf<String>()),
        "Medicina general" to Pair("general", listOf("general", "familiar", "medicina")),
        "Pediatría" to Pair("paediatrics|pediatrics", listOf("pediat", "niño")),
        "Ginecología" to Pair("gynaecology|gynecology|obstetrics", listOf("gineco", "obstet", "materno")),
        "Cardiología" to Pair("cardiology", listOf("cardio", "corazón")),
        "Dermatología" to Pair("dermatology", listOf("dermat", "piel")),
        "Traumatología" to Pair("orthopaedics|orthopedics|traumatology", listOf("trauma", "ortoped")),
        "Oftalmología" to Pair("ophthalmology", listOf("oftalm", "ojos", "visión")),
        "Odontología" to Pair("dentist|dental", listOf("odonto", "dental", "dentista")),
        "Psicología / Psiquiatría" to Pair("psychiatry|psychology", listOf("psico", "psiqui", "mental")),
        "Urología" to Pair("urology", listOf("urolog")),
        "Oncología" to Pair("oncology", listOf("oncol", "cáncer", "neoplás")),
        "Laboratorio / Imágenes" to Pair("radiology|laboratory", listOf("laborat", "imágenes", "radiolog", "diagnóst"))
    )

    val SERVIDORES = listOf("https://overpass-api.de/api/interpreter")

    fun consulta(lat: Double, lon: Double, radioM: Int): String {
        val ar = "(around:$radioM,$lat,$lon)"
        val tipos = listOf("clinic", "hospital", "doctors", "dentist")
        return "[out:json][timeout:25];(" + tipos.joinToString("") { "nwr[\"amenity\"=\"$it\"]$ar;" } + ");out center 400;"
    }

    fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }

    fun norm(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun coincide(nombre: String, etiquetasRaw: String, espOsm: String, claves: List<String>): Boolean {
        if (espOsm.isEmpty()) return true
        val et = norm(etiquetasRaw)
        val n = norm(nombre)
        return espOsm.split("|").any { et.contains(it) } || claves.any { n.contains(norm(it)) }
    }

    private fun mapa(t: JSONObject): Map<String, String> {
        val m = HashMap<String, String>()
        for (k in t.keys()) m[k] = t.optString(k)
        return m
    }

    private fun mapaNominatim(e: JSONObject, ex: JSONObject?, ad: JSONObject?): Map<String, String> {
        val m = HashMap<String, String>()
        if (ex != null) for (k in ex.keys()) m[k] = ex.optString(k)
        if (ad != null) {
            fun poner(k: String, vararg src: String) {
                for (x in src) { val v = ad.optString(x, ""); if (v.isNotEmpty()) { m[k] = v; return } }
            }
            poner("addr:street", "road"); poner("addr:housenumber", "house_number")
            poner("addr:suburb", "suburb", "neighbourhood"); poner("addr:district", "city_district", "district")
            poner("addr:city", "city", "town", "municipality")
        }
        val tipo = e.optString("type", "")
        if (tipo in listOf("clinic", "hospital", "doctors", "dentist") && !m.containsKey("amenity")) m["amenity"] = tipo
        return m
    }

    fun parsear(json: String, lat: Double, lon: Double, espOsm: String, claves: List<String>): List<Clinica> {
        val arr = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = mutableListOf<Clinica>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val t = e.optJSONObject("tags") ?: continue
            val c = e.optJSONObject("center")
            val la = if (e.has("lat")) e.getDouble("lat") else (c?.optDouble("lat") ?: Double.NaN)
            val lo = if (e.has("lon")) e.getDouble("lon") else (c?.optDouble("lon") ?: Double.NaN)
            if (la.isNaN() || lo.isNaN()) continue
            val nombre = t.optString("name", "").ifEmpty { "Centro de salud" }
            val esp = t.optString("healthcare:speciality", "")
            if (!coincide(nombre, esp + " " + t.optString("amenity", "") + " " + t.optString("healthcare", ""), espOsm, claves)) continue
            val dir = listOf(t.optString("addr:street", ""), t.optString("addr:housenumber", "")).filter { it.isNotEmpty() }.joinToString(" ")
            out.add(Clinica(nombre, dir, la, lo, distanciaKm(lat, lon, la, lo).toFloat(), mapa(t)))
        }
        out.sortBy { it.km }
        return out
    }

    fun descargar(q: String): String {
        val errores = mutableListOf<String>()
        for (u in SERVIDORES) {
            val host = URL(u).host
            for (intento in 1..2) {
                var reintentar = false
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"; c.doOutput = true
                    c.connectTimeout = 5000; c.readTimeout = 15000
                    c.setRequestProperty("User-Agent", "CliniFacil/1.0 (Android; buscador de clinicas)")
                    c.setRequestProperty("Accept", "application/json")
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                    val code = c.responseCode
                    val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                    if (code in 200..299 && body.trimStart().startsWith("{")) return body
                    errores.add("$host HTTP $code")
                    reintentar = code in listOf(429, 502, 503, 504)
                } catch (e: Exception) {
                    errores.add("$host ${e.javaClass.simpleName}")
                }
                if (!reintentar) break
                Thread.sleep(2000)
            }
        }
        throw RuntimeException(errores.joinToString("; "))
    }

    fun parsearNominatim(json: String, lat: Double, lon: Double, espOsm: String, claves: List<String>): List<Clinica> {
        val arr = JSONArray(json)
        val out = mutableListOf<Clinica>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val la = e.optString("lat", "").toDoubleOrNull() ?: continue
            val lo = e.optString("lon", "").toDoubleOrNull() ?: continue
            val nombre = e.optString("name", "").ifEmpty { e.optString("display_name", "").substringBefore(",") }.ifEmpty { "Centro de salud" }
            val ex = e.optJSONObject("extratags")
            val etiquetas = (ex?.optString("healthcare:speciality", "") ?: "") + " " + e.optString("type", "") + " " + (ex?.optString("healthcare", "") ?: "")
            if (!coincide(nombre, etiquetas, espOsm, claves)) continue
            val ad = e.optJSONObject("address")
            val dir = listOf(ad?.optString("road", "") ?: "", ad?.optString("house_number", "") ?: "").filter { it.isNotEmpty() }.joinToString(" ")
            out.add(Clinica(nombre, dir, la, lo, distanciaKm(lat, lon, la, lo).toFloat(), mapaNominatim(e, ex, ad)))
        }
        out.sortBy { it.km }
        return out
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "CliniFacil/1.0 (Android; buscador de clinicas)")
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return c.inputStream.bufferedReader().readText()
    }

    fun buscarNominatim(lat: Double, lon: Double, radioKm: Int, especialidad: String, osm: String, claves: List<String>): List<Clinica> {
        val dLat = radioKm / 111.32
        val dLon = radioKm / (111.32 * cos(Math.toRadians(lat)))
        val vb = "${lon - dLon},${lat + dLat},${lon + dLon},${lat - dLat}"
        val consultas = mutableListOf("clinic", "hospital", "doctors", "dentist")
        if (osm.isNotEmpty()) {
            val p = norm(especialidad.split(" ")[0])
            consultas.add(p); consultas.add("clinica $p")
        }
        val vistos = HashSet<String>()
        val out = mutableListOf<Clinica>()
        val errores = mutableListOf<String>()
        for ((i, q) in consultas.withIndex()) {
            if (i > 0) Thread.sleep(1100)
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&extratags=1&bounded=1&limit=40&viewbox=$vb&q=" + URLEncoder.encode(q, "UTF-8")
                for (c in parsearNominatim(get(url), lat, lon, osm, claves)) if (vistos.add("${c.lat},${c.lon},${c.nombre}")) out.add(c)
            } catch (e: Exception) {
                errores.add("${e.javaClass.simpleName} ${e.message ?: ""}".trim())
            }
        }
        if (out.isEmpty() && errores.size == consultas.size) throw RuntimeException("Nominatim: " + errores.first())
        out.sortBy { it.km }
        return out
    }

    fun buscar(lat: Double, lon: Double, radioKm: Int, especialidad: String, aviso: (String) -> Unit = {}): List<Clinica> {
        val (osm, claves) = ESPECIALIDADES[especialidad] ?: Pair("", emptyList<String>())
        val r = try {
            parsear(descargar(consulta(lat, lon, radioKm * 1000)), lat, lon, osm, claves)
        } catch (e: Exception) {
            aviso("Servidor principal ocupado, usando respaldo…")
            try {
                buscarNominatim(lat, lon, radioKm, especialidad, osm, claves)
            } catch (e2: Exception) {
                throw RuntimeException("${e.message}; ${e2.message}")
            }
        }
        return r.filter { it.km <= radioKm + 0.5f }.take(100)
    }
}
