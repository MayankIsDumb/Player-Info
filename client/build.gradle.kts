// Launches the real vanilla Minecraft client (26.x) without Loom or the
// official launcher. Minecraft 26.x ships unobfuscated and Mojang no longer
// publishes mappings, so Loom cannot run it; this task assembles the game
// directly from Mojang's piston-meta version JSON instead:
//   - client jar, libraries and Windows natives under vanilla/
//   - asset index + assets under vanilla/assets/ (sha1-verified, cached)
//   - game data (saves, options.txt) under run/
// and then starts the game on Java 25 with an offline dev account.

plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    // Minecraft 26.x requires Java 25; the launcher spawns the game with the
    // same runtime it runs on.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.register<JavaExec>("runClient") {
    group = "run"
    description = "Downloads (cached) and launches the vanilla Minecraft client."
    mainClass = "dev.negi6.client.ClientLauncher"
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
    args("--username", "Player904", "--game-dir", "run", "--vanilla-dir", "vanilla", "--mc-version", "26.3")
}
