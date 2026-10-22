plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pe.clinifacil"
    compileSdk = 34
    defaultConfig {
        applicationId = "pe.clinifacil"
        minSdk = 26; targetSdk = 34
        versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.configureEach { if (name == "assembleDebug") finalizedBy("testDebugUnitTest") }
tasks.withType<Test>().configureEach {
    ignoreFailures = true
    doFirst { layout.buildDirectory.file("test-info.txt").get().asFile.delete() }
    doLast {
        val out = mutableListOf<String>()
        layout.buildDirectory.dir("test-results").get().asFile.walkTopDown().filter { it.extension == "xml" }.forEach { x ->
            x.readText().split("<testcase ").drop(1).forEach { c ->
                val name = Regex("name=\"([^\"]+)\"").find(c)?.groupValues?.get(1) ?: "?"
                val fail = Regex("<failure message=\"([^\"]*)\"").find(c)
                out.add((if (fail == null) "OK " else "FALLO ") + name + (if (fail != null) " -> " + fail.groupValues[1].take(250) else ""))
            }
        }
        val info = layout.buildDirectory.file("test-info.txt").get().asFile
        if (info.exists()) out.addAll(info.readLines())
        println("::notice title=Resultado de pruebas::" + out.joinToString("%0A"))
    }
}
