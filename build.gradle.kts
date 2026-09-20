plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

val display by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
}
val mannequin by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    add(display.compileOnlyConfigurationName, "io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    add(mannequin.compileOnlyConfigurationName, "io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Both visual features share one resource pack, with metadata for each client generation.
val resourcePackTasks = listOf(false, true).map { legacy ->
    val suffix = if (legacy) "-1.19.4" else ""
    tasks.register<Zip>("bundleResourcePack${if (legacy) "Legacy" else "Modern"}") {
        archiveFileName.set("tracesdeath$suffix.zip")
        destinationDirectory.set(layout.buildDirectory.dir("generated/resource-packs"))
        from("resource-pack") {
            include("assets/**", "pack.png")
            if (!legacy) include("pack.mcmeta")
        }
        if (legacy) from("resource-pack/metadata/1.19.4") { include("pack.mcmeta") }
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks {
    processResources {
        resourcePackTasks.forEach { pack -> from(pack) { into("resource-packs") } }
    }
    compileJava { options.release.set(8) }
    named<JavaCompile>(display.compileJavaTaskName) { options.release.set(17) }
    named<JavaCompile>(mannequin.compileJavaTaskName) { options.release.set(21) }
    jar {
        from(display.output)
        from(mannequin.output)
    }
    test { useJUnitPlatform() }
    runServer {
        minecraftVersion("1.21.9")
        runDirectory.set(file("run-core"))
        jvmArgs("-Xms1G", "-Xmx2G", "-Dterminal.jline=false", "-Dterminal.ansi=false")
    }
}
