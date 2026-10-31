package org.ctrlaltdyleted.thelostages.bootstrap;

import com.google.gson.Gson;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.*;

/** Forge 47.4.20 SERVICE-layer SPI, invoked before ModValidator/ModSorter. */
public final class LostAgesDependencyLocator extends AbstractJarFileDependencyLocator {
    public String name() { return "The Lost Ages integrated dependency installer"; }
    public void initArguments(Map<String, ?> arguments) {}

    public record Installed(String id, String version, Path file) {}
    public record Problem(Dependency dependency, List<Installed> installed) {
        public String detected() { return installed.isEmpty() ? "Missing" : installed.stream().map(Installed::version).toList().toString(); }
    }

    static List<Problem> problems(List<Dependency> catalog, List<Installed> installed) {
        var problems = new ArrayList<Problem>();
        for (var dep : catalog) {
            if (dep.pack()) continue;
            var matches = installed.stream().filter(mod -> dep.id().equals(mod.id())).toList();
            if (matches.size() != 1 || !dep.accepts(matches.get(0).version())) problems.add(new Problem(dep, matches));
        }
        return problems;
    }

    static Path extractGameplay(Path cache) throws IOException {
        Files.createDirectories(cache);
        Path temp = Files.createTempFile(cache, "gameplay-", ".part");
        try {
            try (var in = LostAgesDependencyLocator.class.getResourceAsStream("/META-INF/thelostages/gameplay.jar")) {
                if (in == null) throw new IOException("Embedded gameplay JAR is missing");
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String hash = Installation.hash(temp);
            Path target = cache.resolve("thelostages-" + hash + ".jar");
            if (Files.exists(target)) {
                if (!Installation.hash(target).equals(hash)) throw new IOException("Cached gameplay JAR is corrupt: " + target);
            } else {
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, target); }
            }
            return target;
        } finally { Files.deleteIfExists(temp); }
    }

    @Override public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        Path game = FMLPaths.GAMEDIR.get();
        Path state = game.resolve(".thelostages");
        try {
            Files.createDirectories(state);
            // Held across checking and all installs; concurrent launches cannot race each other.
            try (var channel = FileChannel.open(state.resolve("dependency-manager.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Another Lost Ages launch is checking/installing dependencies. Close it first.");
                var result = createMod(extractGameplay(state.resolve("cache")));
                if (result.ex() != null) throw result.ex();
                IModFile gameplay = result.file();
                if (gameplay == null) throw new IOException("Forge could not read the embedded gameplay mod");
                var installed = new ArrayList<Installed>();
                var allFiles = new ArrayList<IModFile>();
                for (var file : loadedMods) {
                    // Before validation, Forge also supplies libraries without mod metadata.
                    var fileInfo = file.getModFileInfo();
                    if (fileInfo == null) continue;
                    allFiles.add(file);
                    for (var mod : fileInfo.getMods())
                        installed.add(new Installed(mod.getModId(), mod.getVersion().toString(), file.getFilePath()));
                }
                var catalog = Dependency.catalog();
                var problems = problems(catalog, installed);
                if (!problems.isEmpty()) {
                    String instructions = problems.stream().map(p -> p.dependency().name() + " " + p.dependency().range()
                            + " (detected: " + p.detected() + ") " + p.dependency().page()).collect(java.util.stream.Collectors.joining("\n"));
                    Files.writeString(state.resolve("missing-dependencies.txt"), instructions + "\nInstall into " + FMLPaths.MODSDIR.get() + " and restart.\n");
                    if (shouldOpenWindow(FMLLoader.getDist().isClient(), java.awt.GraphicsEnvironment::isHeadless)) {
                        ManagerWindow.showAndWait(game, FMLPaths.MODSDIR.get(), problems, catalog, allFiles);
                        // The user explicitly closed the repair window. End the client process,
                        // including Forge's early display threads, after installation cleanup.
                        System.exit(0);
                    }
                    // Never resume this discovery snapshot, even after successful disk installation.
                    throw new IllegalStateException("The Lost Ages requires a restart after dependency repair.\n" + instructions
                            + "\nDetails: " + state.resolve("missing-dependencies.txt"));
                }
                return List.of(gameplay);
            }
        } catch (IOException e) { throw new IllegalStateException("The Lost Ages dependency bootstrap failed: " + e.getMessage(), e); }
    }

    static boolean shouldOpenWindow(boolean client, java.util.function.BooleanSupplier headless) {
        return client && !headless.getAsBoolean();
    }
}

/** The nine managed add-ons also generate their mandatory Forge dependency declarations. */
record Dependency(String id, String name, String range, String ordering, boolean pack,
                         String page, int projectId, int fileId, String filename, String sha256) {
    public boolean accepts(String version) {
        try {
            return range.equals("*") || VersionRange.createFromVersionSpec(range)
                    .containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) { throw new IllegalArgumentException(range, e); }
    }

    public static List<Dependency> catalog() throws IOException {
        try (var in = Dependency.class.getResourceAsStream("/dependencies.json")) {
            if (in == null) throw new IOException("Dependency catalog missing from release JAR");
            return List.of(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Dependency[].class));
        }
    }
}
