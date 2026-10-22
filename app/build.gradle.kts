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
    val lines = mutableListOf<String>()
    doFirst { layout.buildDirectory.file("test-info.txt").get().asFile.delete() }
    afterTest(KotlinClosure2<TestDescriptor, TestResult, Unit>({ d, r ->
        lines.add("${r.resultType} ${d.name}" + (r.exception?.let { " -> " + (it.message ?: it.javaClass.simpleName).take(300).replace("\n", " ") } ?: ""))
    }))
    doLast {
        val f = layout.buildDirectory.file("test-info.txt").get().asFile
        if (f.exists()) lines.addAll(f.readLines())
        println("::notice title=Resultado de pruebas::" + lines.joinToString("%0A"))
    }
}
