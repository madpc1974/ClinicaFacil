package pe.clinifacil

import org.junit.Assert.*
import org.junit.Test

class DetalleTest {
    @Test fun horario_se_traduce_a_espanol() {
        assertEquals("Lun-Vie 08:00-20:00 · Sáb 08:00-13:00", Detalle.horario("Mo-Fr 08:00-20:00; Sa 08:00-13:00"))
        assertEquals("Abierto las 24 horas", Detalle.horario("24/7"))
        assertEquals("", Detalle.horario(null))
    }

    @Test fun especialidades_por_etiqueta_y_por_nombre() {
        assertEquals("Pediatría, Cardiología", Detalle.especialidades(mapOf("healthcare:speciality" to "paediatrics;cardiology"), "X"))
        assertEquals("Pediatría (según el nombre)", Detalle.especialidades(emptyMap(), "Clínica Pediátrica Sol"))
        assertEquals("", Detalle.especialidades(emptyMap(), "Centro Vida"))
    }

    @Test fun telefono_web_y_direccion() {
        val t = mapOf("phone" to "+51 1 234-5678; 987654321", "website" to "www.clinica.pe",
            "addr:street" to "Av. Arequipa", "addr:housenumber" to "2310", "addr:suburb" to "Lince", "addr:city" to "Lima")
        assertEquals("+5112345678", Detalle.telefonoMarcable(t))
        assertEquals("+51 1 234-5678 / 987654321", Detalle.telefono(t))
        assertEquals("https://www.clinica.pe", Detalle.web(t))
        assertEquals("Av. Arequipa 2310 — Lince, Lima", Detalle.direccion(t))
        assertNull(Detalle.telefonoMarcable(emptyMap()))
        assertNull(Detalle.web(emptyMap()))
    }

    @Test fun extras_y_tipo() {
        val e = Detalle.extras(mapOf("emergency" to "yes", "wheelchair" to "limited", "operator" to "EsSalud", "fee" to "no"))
        assertEquals(listOf("Atención de emergencia" to "Sí", "Acceso en silla de ruedas" to "Limitado",
            "Institución" to "EsSalud", "Atención con costo" to "No, es gratuita"), e)
        assertEquals("Hospital", Detalle.tipo(mapOf("amenity" to "hospital")))
        assertEquals("Establecimiento de salud", Detalle.tipo(emptyMap()))
    }

    @Test fun overpass_conserva_las_etiquetas() {
        val j = """{"elements":[{"type":"node","lat":-12.05,"lon":-77.04,"tags":{"amenity":"clinic","name":"A","phone":"+511","opening_hours":"Mo-Fr 08:00-12:00"}}]}"""
        val c = Overpass.parsear(j, -12.0464, -77.0428, "", emptyList())[0]
        assertEquals("+511", c.tags["phone"])
        assertEquals("clinic", c.tags["amenity"])
    }

    @Test fun nominatim_conserva_extratags_y_direccion() {
        val j = """[{"lat":"-12.05","lon":"-77.04","name":"A","type":"clinic","extratags":{"phone":"+511","opening_hours":"Mo-Fr 08:00-12:00"},"address":{"road":"Av. X","house_number":"1","suburb":"Miraflores","city":"Lima"}}]"""
        val c = Overpass.parsearNominatim(j, -12.0464, -77.0428, "", emptyList())[0]
        assertEquals("+511", c.tags["phone"])
        assertEquals("Miraflores", c.tags["addr:suburb"])
        assertEquals("Lima", c.tags["addr:city"])
        assertEquals("clinic", c.tags["amenity"])
        assertEquals("Av. X 1 — Miraflores, Lima", Detalle.direccion(c.tags))
    }
}
