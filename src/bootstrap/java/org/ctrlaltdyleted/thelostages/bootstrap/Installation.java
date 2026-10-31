package org.ctrlaltdyleted.thelostages.bootstrap;

import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongConsumer;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Verifies browser-downloaded originals and copies them with confirmed backups and rollback. */
final class Installation {
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private Installation() {}

    static Optional<Path> installedPack(Path directory, Dependency dep) throws IOException {
        if (!Files.isDirectory(directory)) return Optional.empty();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .filter(p -> correct(p, dep)).findFirst();
        }
    }

    public static synchronized Path install(Dependency dep, Path source, Path directory, List<Path> conflicts,
                                            Path backups, boolean confirmed, LongConsumer progress) throws IOException {
        if (dep.filename() == null || !Path.of(dep.filename()).getFileName().toString().equals(dep.filename()))
            throw new IOException("Invalid catalog filename; the local file was not installed.");
        validate(source, dep);
        Files.createDirectories(directory);
        if (dep.pack()) {
            var existing = installedPack(directory, dep);
            if (existing.isPresent()) return existing.get();
        }
        Path destination = directory.resolve(dep.filename());
        if (Files.exists(destination) && correct(destination, dep) && conflicts.isEmpty()) return destination;
        if (!conflicts.isEmpty() && !confirmed) throw new IOException("Explicit confirmation is required to move conflicting mods to backup.");
        if (Files.exists(destination) && !conflicts.contains(destination))
            throw new IOException("Destination already exists and was not approved for replacement: " + destination);
        for (Path conflict : conflicts) {
            if (!conflict.toAbsolutePath().normalize().getParent().equals(directory.toAbsolutePath().normalize()) || Files.isSymbolicLink(conflict))
                throw new IOException("Cannot automatically replace a nested, linked, or externally located mod: " + conflict);
        }
        Path temp = Files.createTempFile(directory, ".thelostages-", ".part");
        var moved = new LinkedHashMap<Path, Path>();
        boolean installed = false;
        Exception failure = null;
        try {
            try (var in = Files.newInputStream(source); var out = Files.newOutputStream(temp)) {
                transfer(in, out, Files.size(source), progress);
            }
            validate(temp, dep);
            for (var conflict : conflicts) {
                Files.createDirectories(backups);
                Path backup = backups.resolve(UUID.randomUUID() + "-" + conflict.getFileName() + ".disabled");
                Files.move(conflict, backup); moved.put(conflict, backup);
            }
            // Reserve the destination without REPLACE_EXISTING. Atomic rename of the reserved file
            // is safe while holding the instance lock; unrelated destinations are never replaced.
            Files.createFile(destination);
            installed = true;
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
            validate(destination, dep);
            return destination;
        } catch (IOException | RuntimeException e) {
            failure = e;
            if (installed) try { Files.deleteIfExists(destination); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            for (var entry : moved.entrySet()) {
                try { Files.move(entry.getValue(), entry.getKey()); } catch (IOException rollback) { e.addSuppressed(rollback); }
            }
            throw e;
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                if (failure == null) throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
    }

    static String hash(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Verification cancelled");
                    digest.update(buffer, 0, n);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static String validate(Path file, Dependency dep) throws IOException {
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("Downloaded file is unavailable or exceeds the size limit");
        if (dep.sha256() == null || !hash(file).equalsIgnoreCase(dep.sha256()))
            throw new IOException("SHA-256 mismatch. The file was not installed; retry or use the official download page.");
        try (var zip = new ZipFile(file.toFile())) {
            verifyEntries(zip);
            if (dep.pack()) {
                verifyResourcePack(zip);
            } else {
                return verifyMod(file, zip, dep);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid archive metadata", e); }
        return null; // Resource packs do not declare a Forge mod version.
    }

    private static void verifyEntries(ZipFile zip) throws IOException {
        // Read every entry to detect truncated streams, with an expansion limit.
        long expanded = 0;
        var entries = zip.entries();
        byte[] buffer = new byte[65536];
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var crc = new CRC32();
            try (var in = zip.getInputStream(entry)) {
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Verification cancelled");
                    expanded += n;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Archive expands beyond the safety limit");
                    crc.update(buffer, 0, n);
                }
            }
            if (entry.getCrc() != crc.getValue()) throw new IOException("Invalid archive CRC");
        }
    }

    private static void verifyResourcePack(ZipFile zip) throws IOException {
        var entry = zip.getEntry("pack.mcmeta");
        if (entry == null) throw new IOException("Resource pack has no root pack.mcmeta");
        try (var reader = new InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
            var metadata = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            if (!metadata.getAsJsonObject("pack").has("pack_format")) throw new IOException("Invalid pack metadata");
        }
    }

    private static String verifyMod(Path file, ZipFile zip, Dependency dep) throws IOException {
        var entry = zip.getEntry("META-INF/mods.toml");
        if (entry == null) throw new IOException("Archive is not a Forge mod");
        try (var reader = new InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
            var config = new TomlParser().parse(reader);
            List<com.electronwill.nightconfig.core.Config> mods = config.get("mods");
            String foundVersion = null;
            for (var mod : mods) {
                String version = mod.get("version");
                if ("${file.jarVersion}".equals(version)) {
                    try (var jar = new JarFile(file.toFile())) {
                        version = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
                    }
                }
                if (dep.id().equals(mod.get("modId")) && version != null && dep.accepts(version)) foundVersion = version;
            }
            if (foundVersion == null) throw new IOException("The archive does not declare the expected mod ID and version");
            return foundVersion;
        }
    }

    private static boolean correct(Path file, Dependency dep) {
        try { validate(file, dep); return true; } catch (IOException e) { return false; }
    }

    private static void transfer(InputStream in, OutputStream out, long expected, LongConsumer progress) throws IOException {
        long total = 0;
        byte[] buffer = new byte[65536];
        for (int n; (n = in.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Installation cancelled");
            total += n;
            if (total > MAX_BYTES) throw new IOException("File exceeds size limit");
            out.write(buffer, 0, n);
            progress.accept(total);
        }
        if (expected >= 0 && total != expected) throw new IOException("File changed while copying; retry.");
    }

    static java.net.URI officialPage(Dependency dep) throws IOException {
        java.net.URI uri;
        try { uri = java.net.URI.create(dep.page()); } catch (IllegalArgumentException e) { throw new IOException("Invalid official download page", e); }
        String kind = dep.pack() ? "texture-packs" : "mc-mods";
        if (!"https".equals(uri.getScheme()) || !"www.curseforge.com".equals(uri.getHost()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/minecraft/" + kind + "/[a-z0-9-]+/download/" + dep.fileId()))
            throw new IOException("Only the catalog's exact official CurseForge file pages can be opened");
        return uri;
    }

    static Path initialDownloadFolder(Path game) {
        try {
            Path saved = game.resolve(".thelostages/download-folder.txt");
            if (Files.isRegularFile(saved) && Files.size(saved) < 16384) {
                Path path = Path.of(Files.readString(saved).strip());
                if (usableDirectory(path)) return path.toRealPath();
            }
        } catch (IOException | RuntimeException ignored) { /* Fall back to a visible, conventional location. */ }
        return defaultDownloadFolder(Path.of(System.getProperty("user.home")), System.getProperty("os.name", ""), System.getenv("XDG_CONFIG_HOME"));
    }

    static Path defaultDownloadFolder(Path home, String os, String xdgConfig) {
        if (os.toLowerCase(Locale.ROOT).contains("linux")) {
            try {
                Path config = xdgConfig == null || xdgConfig.isBlank() || !Path.of(xdgConfig).isAbsolute() ? home.resolve(".config") : Path.of(xdgConfig);
                Path file = config.resolve("user-dirs.dirs");
                if (Files.isRegularFile(file) && Files.size(file) < 65536) {
                    var match = java.util.regex.Pattern.compile("(?m)^XDG_DOWNLOAD_DIR=\"([^\"]*)\"\s*$").matcher(Files.readString(file));
                    if (match.find()) {
                        String value = match.group(1).replace("${HOME}", home.toString()).replace("$HOME", home.toString());
                        if (!value.contains("$") && !value.contains("`")) {
                            Path path = Path.of(value);
                            if (path.isAbsolute() && usableDirectory(path) && !Files.isSameFile(path, home)) return path.toRealPath();
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) { /* No shell evaluation or home-directory scan. */ }
        }
        // Java 17 has no public Windows Known Folder/Downloads API. Do not use registry or internal JDK APIs.
        Path conventional = home.resolve("Downloads");
        try { return usableDirectory(conventional) ? conventional.toRealPath() : null; }
        catch (IOException e) { return null; }
    }

    private static boolean usableDirectory(Path path) { return Files.isDirectory(path) && Files.isReadable(path); }

    record Found(Path file, String status) {}
    record Snapshot(Path folder, String message, java.util.Map<String, Found> files) {}

    /** One nonrecursive directory, one background worker, with bounded per-candidate validation caching. */
    static final class Downloads implements AutoCloseable {
        private final Path game;
        private final List<Dependency> catalog;
        private final java.util.function.Consumer<Snapshot> listener;
        private final java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Lost Ages download folder"));
        private final java.util.Map<Path, Candidate> candidates = new java.util.HashMap<>();
        private volatile boolean closed;
        private volatile java.nio.file.WatchService watch;
        private Path folder;
        private String message = "Choose Download Folder to locate your browser downloads.";
        private Snapshot last;
        private long lastScan;
        private boolean pending;
        private record Stamp(long size, java.nio.file.attribute.FileTime modified, Object key) {}
        private static final class Candidate {
            final Stamp stamp; final long since;
            Found result;
            Candidate(Stamp stamp, long now) { this.stamp = stamp; this.since = now; }
        }
        Downloads(Path game, List<Dependency> catalog, java.util.function.Consumer<Snapshot> listener) {
            this.game = game; this.catalog = List.copyOf(catalog); this.listener = listener;
            executor.execute(() -> { Path initial = initialDownloadFolder(game); if (initial != null) change(initial, false); else emit(java.util.Map.of()); });
            executor.scheduleWithFixedDelay(this::tick, 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        void choose(Path directory) { if (!closed) executor.execute(() -> change(directory, true)); }
        void rescan() { if (!closed) executor.execute(() -> { candidates.clear(); lastScan = 0; }); }
        private void change(Path directory, boolean save) {
            if (closed) return;
            try {
                if (!usableDirectory(directory)) throw new IOException("Download folder is unavailable or unreadable");
                Path resolved = directory.toRealPath();
                if (Files.isSameFile(resolved, Path.of(System.getProperty("user.home")))) throw new IOException("Choose a download folder, not the entire home directory");
                closeWatch(); folder = resolved; candidates.clear(); lastScan = 0;
                message = "Watching this folder"; register();
                if (save) {
                    Path config = game.resolve(".thelostages/download-folder.txt"); Files.createDirectories(config.getParent());
                    Files.writeString(config, folder.toString());
                }
                scan();
            } catch (IOException | RuntimeException e) { message = e.getMessage(); emit(java.util.Map.of()); }
        }
        private synchronized void register() {
            if (closed) return;
            try {
                watch = folder.getFileSystem().newWatchService();
                folder.register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY, java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
            } catch (IOException | UnsupportedOperationException e) { closeWatch(); message = "Watching with periodic folder checks"; }
        }
        private void tick() {
            if (closed) return;
            try {
                boolean dirty = false;
                if (watch != null) {
                    java.nio.file.WatchKey key;
                    while ((key = watch.poll()) != null) {
                        for (var event : key.pollEvents()) {
                            if (event.kind() == java.nio.file.StandardWatchEventKinds.OVERFLOW) candidates.clear();
                            else if (event.context() instanceof Path path) candidates.remove(folder.resolve(path));
                            dirty = true;
                        }
                        if (!key.reset()) { closeWatch(); dirty = true; }
                    }
                }
                long now = System.nanoTime();
                if (dirty || pending || now - lastScan > java.util.concurrent.TimeUnit.SECONDS.toNanos(3)) scan();
            } catch (IOException | RuntimeException e) {
                if (!closed) { message = "Download folder unavailable. Choose Download Folder."; candidates.clear(); emit(java.util.Map.of()); }
            }
        }
        private void scan() throws IOException {
            if (folder == null || closed) return;
            if (!usableDirectory(folder)) throw new IOException("Download folder unavailable");
            if (watch == null) register();
            if (message.startsWith("Download folder unavailable")) message = "Watching this folder";
            long now = System.nanoTime(); lastScan = now; pending = false;
            java.util.Map<String, Found> found = new java.util.HashMap<>();
            java.util.Set<Path> present = new java.util.HashSet<>();
            try (var files = Files.newDirectoryStream(folder)) {
                for (Path path : files) {
                    if (closed || Thread.currentThread().isInterrupted()) return;
                    Dependency dep = catalog.stream().filter(d -> candidateName(path.getFileName().toString(), d.filename())).findFirst().orElse(null);
                    if (dep == null || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                    present.add(path);
                    var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    Stamp stamp = new Stamp(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey());
                    Candidate candidate = candidates.get(path);
                    if (candidate == null || !candidate.stamp.equals(stamp)) { candidate = new Candidate(stamp, now); candidates.put(path, candidate); }
                    if (candidate.result == null) {
                        if (now - candidate.since < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1500)) {
                            pending = true;
                        } else {
                            var progress = new java.util.HashMap<String, Found>();
                            if (last != null) progress.putAll(last.files());
                            Found available = found.get(dep.id());
                            Found previous = progress.get(dep.id());
                            if ((available == null || available.file() == null)
                                    && (previous == null || previous.file() == null || path.equals(previous.file()))) {
                                progress.put(dep.id(), new Found(null, "Verifying")); emit(progress);
                            }
                            try {
                                validate(path, dep);
                                var after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                                if (!stamp.equals(new Stamp(after.size(), after.lastModifiedTime(), after.fileKey()))) { candidates.remove(path); pending = true; }
                                else candidate.result = new Found(path, "Ready to install");
                            } catch (IOException | RuntimeException e) { candidate.result = new Found(null, "Invalid file"); }
                        }
                    }
                    Found result = candidate.result == null ? new Found(null, "Download detected") : candidate.result;
                    Found previous = found.get(dep.id());
                    if (previous == null || previous.file() == null && result.file() != null || "Verifying".equals(previous.status())) found.put(dep.id(), result);
                }
            }
            candidates.keySet().retainAll(present);
            emit(found);
        }
        static boolean candidateName(String actual, String expected) {
            int dot = expected.lastIndexOf('.');
            if (dot < 0) return false;
            String extension = expected.substring(dot);
            if (!actual.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) return false;
            String stem = actual.substring(0, actual.length() - extension.length()).replaceFirst("\\s*\\(\\d+\\)$", "");
            return stem.equalsIgnoreCase(expected.substring(0, dot));
        }
        private void emit(java.util.Map<String, Found> files) {
            Snapshot snapshot = new Snapshot(folder, message, java.util.Map.copyOf(files));
            if (!closed && !snapshot.equals(last)) { last = snapshot; listener.accept(snapshot); }
        }
        private synchronized void closeWatch() {
            var service = watch; watch = null;
            if (service != null) try { service.close(); } catch (IOException ignored) {}
        }
        public void close() { closed = true; executor.shutdownNow(); closeWatch(); }
    }
}
