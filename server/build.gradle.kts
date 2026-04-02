plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.paisho.server.ApplicationKt")
}

dependencies {
    implementation(project(":core"))

    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-server-call-logging:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    implementation("org.jetbrains.exposed:exposed-core:0.53.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.53.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.53.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.53.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:2.3.12")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("paisho-server")
    archiveClassifier.set("")
    archiveVersion.set("")
}
