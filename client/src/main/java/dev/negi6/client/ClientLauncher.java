package dev.negi6.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal vanilla client launcher assembled from Mojang's piston-meta version
 * JSON. Minecraft 26.x ships unobfuscated, so the client jar runs directly —
 * no mappings, no mod loader, no official launcher needed. Everything is
 * downloaded once (sha1-verified) into the vanilla directory and reused.
 */
public final class ClientLauncher {

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static String username = "Player";
    private static String mcVersion = "26.3";
    private static Path vanillaDir;
    private static Path gameDir;

    private record Download(String url, Path target, String sha1, Long size) {
        private Download(String url, Path target) {
            this(url, target, null, null);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username" -> username = args[++i];
                case "--game-dir" -> gameDir = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--vanilla-dir" -> vanillaDir = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--mc-version" -> mcVersion = args[++i];
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        Files.createDirectories(gameDir);
        Files.createDirectories(vanillaDir);

        JsonObject version = loadVersionJson();

        Path versionsDir = vanillaDir.resolve("versions").resolve(mcVersion);
        Files.createDirectories(versionsDir);
        JsonObject clientDownload = version.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = versionsDir.resolve(mcVersion + ".jar");
        download(new Download(clientDownload.get("url").getAsString(), clientJar,
                clientDownload.get("sha1").getAsString(), clientDownload.get("size").getAsLong()));

        // Libraries + Windows natives.
        Path nativesDir = vanillaDir.resolve("natives").resolve(mcVersion);
        Files.createDirectories(nativesDir);
        List<Path> classpath = new ArrayList<>();
        classpath.add(clientJar);
        List<Download> nativesJars = new ArrayList<>();
        for (JsonElement libEl : version.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            if (!allowedOnWindows(lib) || !lib.has("downloads")
                    || !lib.getAsJsonObject("downloads").has("artifact")) {
                continue;
            }
            JsonObject artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            Path target = vanillaDir.resolve("libraries").resolve(artifact.get("path").getAsString());
            download(new Download(artifact.get("url").getAsString(), target,
                    artifact.get("sha1").getAsString(), artifact.get("size").getAsLong()));
            classpath.add(target);

            JsonObject classifiers = lib.getAsJsonObject("downloads").getAsJsonObject("classifiers");
            if (classifiers != null && classifiers.has("natives-windows")) {
                JsonObject nat = classifiers.getAsJsonObject("natives-windows");
                nativesJars.add(new Download(nat.get("url").getAsString(),
                        vanillaDir.resolve("libraries").resolve(nat.get("path").getAsString()),
                        nat.get("sha1").getAsString(), nat.get("size").getAsLong()));
            }
        }
        for (Download nativesJar : nativesJars) {
            download(nativesJar);
            extractNatives(nativesJar.target(), nativesDir);
        }

        // Asset index + assets.
        JsonObject assetIndexMeta = version.getAsJsonObject("assetIndex");
        Path indexesDir = vanillaDir.resolve("assets").resolve("indexes");
        Files.createDirectories(indexesDir);
        Path indexPath = indexesDir.resolve(assetIndexMeta.get("id").getAsString() + ".json");
        download(new Download(assetIndexMeta.get("url").getAsString(), indexPath));
        JsonObject index = JsonParser.parseReader(
                Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Download> assets = new ArrayList<>();
        for (var entry : index.getAsJsonObject("objects").entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            assets.add(new Download(
                    "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                    vanillaDir.resolve("assets").resolve("objects")
                            .resolve(hash.substring(0, 2)).resolve(hash),
                    hash, obj.get("size").getAsLong()));
        }
        System.out.println("[ClientLauncher] " + assets.size() + " assets to verify/download ...");
        downloadAllParallel(assets);

        // Launch.
        String mainClass = version.get("mainClass").getAsString();
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString());
        cmd.add("-Xmx2G");
        // Write JVM native-crash logs where we can find them (e.g. 0xC0000005).
        cmd.add("-XX:ErrorFile=" + gameDir.resolve("hs_err_pid%p.log"));
        cmd.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        cmd.add("-cp");
        cmd.add(String.join(";", classpath.stream().map(p -> p.toAbsolutePath().toString()).toList()));
        cmd.add(mainClass);
        cmd.add("--username");
        cmd.add(username);
        cmd.add("--version");
        cmd.add(mcVersion);
        cmd.add("--gameDir");
        cmd.add(gameDir.toString());
        cmd.add("--assetsDir");
        cmd.add(vanillaDir.resolve("assets").toAbsolutePath().toString());
        cmd.add("--assetIndex");
        cmd.add(assetIndexMeta.get("id").getAsString());
        cmd.add("--accessToken");
        cmd.add("0"); // invalid token = offline mode with the username above

        System.out.println("[ClientLauncher] Starting Minecraft " + mcVersion + " as " + username + " ...");
        Process game = new ProcessBuilder(cmd).directory(gameDir.toFile()).inheritIO().start();
        int exit = game.waitFor();
        System.out.println("[ClientLauncher] Game exited with code " + exit);
        System.exit(exit);
    }

    private static JsonObject loadVersionJson() throws IOException {
        JsonObject manifest = fetchJson(MANIFEST_URL);
        String versionUrl = null;
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (mcVersion.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IllegalStateException("Minecraft version not found: " + mcVersion);
        }
        Path versionsDir = vanillaDir.resolve("versions").resolve(mcVersion);
        Files.createDirectories(versionsDir);
        Path versionJson = versionsDir.resolve(mcVersion + ".json");
        download(new Download(versionUrl, versionJson, null, null));
        return JsonParser.parseReader(
                Files.newBufferedReader(versionJson, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject fetchJson(String url) throws IOException {
        try (InputStream in = URI.create(url).toURL().openStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean allowedOnWindows(JsonObject lib) {
        if (!lib.has("rules")) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement ruleEl : lib.getAsJsonArray("rules")) {
            JsonObject rule = ruleEl.getAsJsonObject();
            JsonObject os = rule.has("os") ? rule.getAsJsonObject("os") : null;
            boolean applies = os == null
                    || ("windows".equals(os.get("name").getAsString()));
            if (applies) {
                allowed = "allow".equals(rule.get("action").getAsString());
            }
        }
        return allowed;
    }

    private static void downloadAllParallel(List<Download> downloads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Download d : downloads) {
                futures.add(pool.submit(() -> {
                    download(d);
                    int n = done.incrementAndGet();
                    if (n % 500 == 0 || n == downloads.size()) {
                        System.out.println("[ClientLauncher] assets ready: " + n + "/" + downloads.size());
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    private static void download(Download d) {
        try {
            if (Files.exists(d.target())
                    && (d.size() == null || Files.size(d.target()) == d.size())
                    && (d.sha1() == null || d.sha1().equals(sha1Of(d.target())))) {
                return;
            }
            Files.createDirectories(d.target().getParent());
            for (int attempt = 1; ; attempt++) {
                try (InputStream in = URI.create(d.url()).toURL().openStream()) {
                    Files.copy(in, d.target(), StandardCopyOption.REPLACE_EXISTING);
                }
                if (d.sha1() == null || d.sha1().equals(sha1Of(d.target()))) {
                    return;
                }
                if (attempt >= 3) {
                    throw new IOException("sha1 mismatch after 3 attempts: " + d.url());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("download failed: " + d.url(), e);
        }
    }

    private static void extractNatives(Path jar, Path targetDir) {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("META-INF")
                        || !(name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".jnilib"))) {
                    continue;
                }
                Path out = targetDir.resolve(name.substring(name.lastIndexOf('/') + 1));
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new RuntimeException("native extraction failed: " + jar, e);
        }
    }

    private static String sha1Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("hash failed: " + file, e);
        }
    }
}
