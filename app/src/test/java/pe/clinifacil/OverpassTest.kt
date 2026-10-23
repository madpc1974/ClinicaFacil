package pe.clinifacil

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OverpassTest {
    private fun info(m: String) { File("build/test-info.txt").appendText(m + "\n") }

    private val json = """{"elements":[
      {"type":"node","lat":-12.0500,"lon":-77.0400,"tags":{"amenity":"clinic","name":"Clínica Pediátrica San Juan","addr:street":"Av. Arequipa","addr:housenumber":"2310"}},
      {"type":"way","center":{"lat":-12.0600,"lon":-77.0500},"tags":{"amenity":"hospital","name":"Hospital del Niño"}},
      {"type":"node","lat":-12.0470,"lon":-77.0430,"tags":{"amenity":"clinic","name":"Centro Médico Lima","healthcare:speciality":"cardiology"}},
      {"type":"node","lat":-12.0480,"lon":-77.0420,"tags":{"amenity":"doctors"}},
      {"type":"node","lat":-12.0490,"lon":-77.0410},
      {"type":"relation","tags":{"amenity":"hospital","name":"Sin coordenadas"}}
    ]}"""

    @Test fun distancia_un_grado_de_longitud_en_lima() {
        val d = Overpass.distanciaKm(-12.0464, -77.0428, -12.0464, -77.0338)
        assertEquals(0.98, d, 0.05)
    }

    @Test fun consulta_incluye_radio_y_coordenadas() {
        val q = Overpass.consulta(-12.0464, -77.0428, 5000)
        assertTrue(q.contains("around:5000,-12.0464,-77.0428"))
        assertTrue(q.contains("[out:json]"))
    }

    @Test fun todas_devuelve_con_nombre_o_sin_nombre_pero_con_coordenadas_y_ordenado() {
        val r = Overpass.parsear(json, -12.0464, -77.0428, "", emptyList())
        assertEquals(4, r.size)
        assertTrue(r.zipWithNext().all { it.first.km <= it.second.km })
        assertTrue(r.any { it.nombre == "Centro de salud" })
        assertEquals("Av. Arequipa 2310", r.first { it.nombre.startsWith("Clínica Ped") }.detalle)
    }

    @Test fun pediatria_filtra_por_nombre_incluyendo_way_con_center() {
        val (osm, claves) = Overpass.ESPECIALIDADES["Pediatría"]!!
        val r = Overpass.parsear(json, -12.0464, -77.0428, osm, claves)
        assertEquals(setOf("Clínica Pediátrica San Juan", "Hospital del Niño"), r.map { it.nombre }.toSet())
    }

    @Test fun cardiologia_filtra_por_etiqueta_osm() {
        val (osm, claves) = Overpass.ESPECIALIDADES["Cardiología"]!!
        val r = Overpass.parsear(json, -12.0464, -77.0428, osm, claves)
        assertEquals(listOf("Centro Médico Lima"), r.map { it.nombre })
    }

    @Test fun respuesta_vacia_no_falla() {
        assertTrue(Overpass.parsear("""{"elements":[]}""", 0.0, 0.0, "", emptyList()).isEmpty())
    }

    @Test fun acentos_y_mayusculas_no_impiden_el_filtro() {
        val j = """{"elements":[{"type":"node","lat":-12.05,"lon":-77.04,"tags":{"name":"PEDIATRÍA INFANTIL SAN JOSÉ"}}]}"""
        val (osm, claves) = Overpass.ESPECIALIDADES["Pediatría"]!!
        assertEquals(1, Overpass.parsear(j, -12.0464, -77.0428, osm, claves).size)
    }

    @Test fun etiqueta_pediatrics_en_ingles_americano() {
        val j = """{"elements":[{"type":"node","lat":-12.05,"lon":-77.04,"tags":{"name":"Centro Vida","healthcare:speciality":"pediatrics"}}]}"""
        val (osm, claves) = Overpass.ESPECIALIDADES["Pediatría"]!!
        assertEquals(1, Overpass.parsear(j, -12.0464, -77.0428, osm, claves).size)
    }

    @Test fun odontologia_por_amenity_dentist_sin_palabras_en_el_nombre() {
        val j = """{"elements":[{"type":"node","lat":-12.05,"lon":-77.04,"tags":{"amenity":"dentist","name":"Sonrisas"}}]}"""
        val (osm, claves) = Overpass.ESPECIALIDADES["Odontología"]!!
        assertEquals(1, Overpass.parsear(j, -12.0464, -77.0428, osm, claves).size)
    }

    private fun sonda(url: String, post: String?): String {
        val t0 = System.currentTimeMillis()
        val r = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "CliniFacil/1.0 (Android; buscador de clinicas)")
            if (post != null) { c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(("data=" + URLEncoder.encode(post, "UTF-8")).toByteArray()) } }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            "HTTP $code, ${body.length} bytes"
        } catch (e: Exception) { e.javaClass.simpleName + " " + (e.message ?: "").take(50) }
        return "$r (${System.currentTimeMillis() - t0} ms)"
    }

    @Test fun diagnostico_servidores() {
        for (u in Overpass.SERVIDORES) {
            val h = URL(u).host
            info("DIAG $h ping: " + sonda(u, "[out:json][timeout:5];out count;"))
            info("DIAG $h consulta 1 km: " + sonda(u, Overpass.consulta(-12.1211, -77.0297, 1000)))
        }
        info("DIAG nominatim: " + sonda("https://nominatim.openstreetmap.org/search?q=clinica&format=json&viewbox=-77.05,-12.10,-77.01,-12.14&bounded=1&limit=5", null))
    }

    @Test fun consulta_real_miraflores() {
        val r = try { Overpass.buscar(-12.1211, -77.0297, 5, "Todas las especialidades") }
                catch (e: Exception) { info("REAL ERROR: ${e.message}"); throw e }
        info("REAL Miraflores 5 km, todas: ${r.size} resultados. Primeros: " +
             r.take(3).joinToString { "${it.nombre} (${"%.1f".format(it.km)} km)" })
        assertTrue("Overpass devolvio 0 resultados", r.isNotEmpty())
        assertTrue(r.zipWithNext().all { it.first.km <= it.second.km })
        assertTrue(r.all { it.km <= 5.5f })
        assertTrue(r.size <= 100)
        for (esp in listOf("Pediatría", "Cardiología", "Odontología")) {
            val x = Overpass.buscar(-12.1211, -77.0297, 5, esp)
            info("REAL Miraflores 5 km, $esp: ${x.size} resultados")
        }
    }
}
