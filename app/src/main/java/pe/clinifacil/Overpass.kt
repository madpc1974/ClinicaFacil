package pe.clinifacil

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Clinica(val nombre: String, val detalle: String, val lat: Double, val lon: Double, val km: Float)

object Overpass {
    val ESPECIALIDADES = linkedMapOf(
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

    val SERVIDORES = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    fun consulta(lat: Double, lon: Double, radioM: Int): String =
        "[out:json][timeout:25];nwr[\"amenity\"~\"clinic|hospital|doctors|dentist\"](around:$radioM,$lat,$lon);out center 150;"

    fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
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
            if (espOsm.isNotEmpty() && !(esp.contains(espOsm) || claves.any { nombre.lowercase().contains(it) })) continue
            val dir = listOf(t.optString("addr:street", ""), t.optString("addr:housenumber", "")).filter { it.isNotEmpty() }.joinToString(" ")
            out.add(Clinica(nombre, dir, la, lo, distanciaKm(lat, lon, la, lo).toFloat()))
        }
        out.sortBy { it.km }
        return out
    }

    fun descargar(q: String): String {
        val errores = mutableListOf<String>()
        for (u in SERVIDORES) {
            val host = URL(u).host
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 15000; c.readTimeout = 40000
                c.setRequestProperty("User-Agent", "CliniFacil/1.0 (Android; buscador de clinicas)")
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                if (code in 200..299 && body.trimStart().startsWith("{")) return body
                errores.add("$host HTTP $code")
            } catch (e: Exception) {
                errores.add("$host ${e.javaClass.simpleName}")
            }
        }
        throw RuntimeException(errores.joinToString("; "))
    }

    fun buscar(lat: Double, lon: Double, radioKm: Int, especialidad: String): List<Clinica> {
        val (osm, claves) = ESPECIALIDADES[especialidad] ?: Pair("", emptyList<String>())
        return parsear(descargar(consulta(lat, lon, radioKm * 1000)), lat, lon, osm, claves)
    }
}
