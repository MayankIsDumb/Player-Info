plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.negi6"
version = "0.1.0"
description = "Shows information about players."

java {
    // Java 21 bytecode runs on every server from 1.21 (Java 21) through 26.x (Java 25).
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
}

dependencies {
    // Compiled against the OLDEST supported API so one jar runs on everything
    // from Paper 1.21 through 26.x. Newer-only constants are resolved at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // For reading offline players' vanilla stats files (world/stats/<uuid>.json).
    compileOnly("com.google.code.gson:gson:2.11.0")
    // Optional integration: PlaceholderAPI placeholders (%playerinfo_*%).
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        // Boots a local Paper test server in ./run with this plugin loaded.
        // EULA is pre-accepted in run/eula.txt.
        minecraftVersion("26.3")
        // 26.x needs Java 25 even though the plugin itself compiles for Java 21.
        javaLauncher.set(project.the<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
        })
    }
}
