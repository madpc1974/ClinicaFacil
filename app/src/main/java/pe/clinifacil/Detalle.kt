package pe.clinifacil

object Detalle {
    private fun primero(tags: Map<String, String>, vararg claves: String): String? =
        claves.firstNotNullOfOrNull { k -> tags[k]?.trim()?.takeIf { it.isNotEmpty() } }

    fun tipo(tags: Map<String, String>): String = when (tags["amenity"]) {
        "clinic" -> "Clínica / centro médico"
        "hospital" -> "Hospital"
        "doctors" -> "Consultorio médico"
        "dentist" -> "Consultorio dental"
        else -> "Establecimiento de salud"
    }

    fun direccion(tags: Map<String, String>): String {
        val calle = listOf(tags["addr:street"], tags["addr:housenumber"]).filter { !it.isNullOrBlank() }.joinToString(" ")
        val zona = listOf(tags["addr:suburb"], tags["addr:district"], tags["addr:city"]).filter { !it.isNullOrBlank() }.distinct().joinToString(", ")
        return listOf(calle, zona).filter { it.isNotBlank() }.joinToString(" — ")
    }

    fun horario(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        if (raw.trim() == "24/7") return "Abierto las 24 horas"
        val dias = mapOf("Mo" to "Lun", "Tu" to "Mar", "We" to "Mié", "Th" to "Jue", "Fr" to "Vie",
            "Sa" to "Sáb", "Su" to "Dom", "PH" to "feriados", "off" to "cerrado")
        return Regex("\\b(Mo|Tu|We|Th|Fr|Sa|Su|PH|off)\\b").replace(raw) { dias[it.value] ?: it.value }.replace(";", " ·")
    }

    fun especialidades(tags: Map<String, String>, nombre: String): String {
        val todas = Overpass.ESPECIALIDADES.filterKeys { it != "Todas las especialidades" }
        val raw = tags["healthcare:speciality"] ?: ""
        if (raw.isNotBlank()) {
            val et = Overpass.norm(raw)
            val m = todas.filter { (_, v) -> v.first.split("|").any { et.contains(it) } }.keys
            return if (m.isNotEmpty()) m.joinToString(", ") else raw.replace(";", ", ").replace("_", " ")
        }
        val n = Overpass.norm(nombre)
        val m = todas.filter { (_, v) -> v.second.any { n.contains(Overpass.norm(it)) } }.keys
        return if (m.isNotEmpty()) m.joinToString(", ") + " (según el nombre)" else ""
    }

    fun telefono(tags: Map<String, String>): String? =
        primero(tags, "phone", "contact:phone", "mobile", "contact:mobile")?.replace(";", " / ")

    fun telefonoMarcable(tags: Map<String, String>): String? =
        primero(tags, "phone", "contact:phone", "mobile", "contact:mobile")
            ?.split(";")?.first()?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotEmpty() }

    fun web(tags: Map<String, String>): String? =
        primero(tags, "website", "contact:website", "url")?.let { if (it.startsWith("http")) it else "https://$it" }

    fun extras(tags: Map<String, String>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        primero(tags, "email", "contact:email")?.let { out.add("Correo" to it) }
        when (tags["emergency"]) { "yes" -> out.add("Atención de emergencia" to "Sí"); "no" -> out.add("Atención de emergencia" to "No") }
        when (tags["wheelchair"]) { "yes" -> out.add("Acceso en silla de ruedas" to "Sí"); "limited" -> out.add("Acceso en silla de ruedas" to "Limitado"); "no" -> out.add("Acceso en silla de ruedas" to "No") }
        primero(tags, "operator")?.let { out.add("Institución" to it) }
        primero(tags, "description:es", "description")?.let { out.add("Descripción" to it) }
        when (tags["fee"]) { "yes" -> out.add("Atención con costo" to "Sí"); "no" -> out.add("Atención con costo" to "No, es gratuita") }
        return out
    }
}
