plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.9")
        runDirectory.set(file("run-core"))
        jvmArgs("-Xms1G", "-Xmx2G", "-Dterminal.jline=false", "-Dterminal.ansi=false")
    }

}
