package org.ctrlaltdyleted.forgefrontierlostages.install;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public final class ManagedFileInstaller
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INDEX_RESOURCE = "forgefrontierlostages/managed-files.txt";
    private static final String LEGACY_LOST_AGES_FOLDER = "lost_ages";
    private static final List<String> OBSOLETE_MANAGED_PATHS = List.of(
            "kubejs/server_scripts/forgefrontierlostages_ae2_changes.js",
            "kubejs/server_scripts/forgefrontierlostages_botany_pot_changes.js",
            "kubejs/startup_scripts/lostages_bootstrap.js",
            "kubejs/client_scripts/lostages_hide_ae2_facades.js",
            managedPath("kubejs", "server_scripts", LEGACY_LOST_AGES_FOLDER, "AE2_Changes.js"),
            managedPath("kubejs", "server_scripts", LEGACY_LOST_AGES_FOLDER, "Botany_Pots_Changes.js"),
            managedPath("kubejs", "server_scripts", LEGACY_LOST_AGES_FOLDER, "Easy_Villagers_Changes.js"),
            managedPath("kubejs", "startup_scripts", LEGACY_LOST_AGES_FOLDER, "Lost_Ages_Bootstrap.js"),
            managedPath("kubejs", "startup_scripts", "Lost Ages", "Lost_Ages_Bootstrap.js"),
            managedPath("kubejs", "client_scripts", LEGACY_LOST_AGES_FOLDER, "Hide_AE2_Facades.js"),
            managedPath("kubejs", "server_scripts", "Lost Ages", "AE2_Changes.js"),
            managedPath("kubejs", "server_scripts", "Lost Ages", "Botany_Pots_Changes.js"),
            managedPath("kubejs", "server_scripts", "Lost Ages", "Easy_Villagers_Changes.js"),
            managedPath("kubejs", "client_scripts", "Lost Ages", "Hide_AE2_Facades.js")
    );

    private ManagedFileInstaller()
    {
    }

    public static void install()
    {
        final Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        removeObsoleteManagedFiles(gameDir);
        final List<String> lines = readIndex();

        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++)
        {
            final String rawLine = lines.get(lineNumber - 1);
            final String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            final String[] parts = line.split("\\|", -1);
            if (parts.length != 2)
            {
                throw new RuntimeException("Invalid managed-files entry at line " + lineNumber + ": " + rawLine);
            }

            final String resourcePath = parts[0].trim();
            final String relativePathText = parts[1].trim();
            validatePathToken(resourcePath, "resource path", lineNumber);
            validatePathToken(relativePathText, "game-relative path", lineNumber);

            final Path relativePath = Paths.get(relativePathText).normalize();
            if (relativePath.startsWith(".."))
            {
                throw new RuntimeException("Rejected traversal in managed-files entry at line " + lineNumber + ": " + rawLine);
            }

            final Path targetPath = gameDir.resolve(relativePath).normalize();
            if (!targetPath.startsWith(gameDir))
            {
                throw new RuntimeException("Managed file target escapes game directory at line " + lineNumber + ": " + rawLine);
            }

            final byte[] bundledBytes = readBundledResource(resourcePath, lineNumber);
            copyIfChanged(resourcePath, targetPath, bundledBytes);
        }

        removeObsoleteLostAgesDirectories(gameDir);
    }

    private static void removeObsoleteManagedFiles(Path gameDir)
    {
        for (String relativePathText : OBSOLETE_MANAGED_PATHS)
        {
            final Path relativePath = Paths.get(relativePathText).normalize();
            if (relativePath.startsWith(".."))
            {
                LOGGER.warn("Skipping obsolete managed path with traversal: {}", relativePathText);
                continue;
            }

            final Path obsoletePath = gameDir.resolve(relativePath).normalize();
            if (!obsoletePath.startsWith(gameDir))
            {
                LOGGER.warn("Skipping obsolete managed path outside game directory: {}", obsoletePath);
                continue;
            }

            try
            {
                if (!Files.exists(obsoletePath))
                {
                    LOGGER.debug("Obsolete managed file already absent: {}", obsoletePath);
                    continue;
                }

                if (!Files.isRegularFile(obsoletePath))
                {
                    LOGGER.warn("Obsolete managed path exists but is not a file: {}", obsoletePath);
                    continue;
                }

                Files.delete(obsoletePath);
                LOGGER.info("Removed obsolete managed file: {}", obsoletePath);
            }
            catch (IOException e)
            {
                LOGGER.error("Failed removing obsolete managed file: {}", obsoletePath, e);
            }
        }
    }

    private static void removeObsoleteLostAgesDirectories(Path gameDir)
    {
        final List<Path> obsoleteDirectories = List.of(
                gameDir.resolve("kubejs").resolve("server_scripts").resolve(LEGACY_LOST_AGES_FOLDER).normalize(),
                gameDir.resolve("kubejs").resolve("startup_scripts").resolve(LEGACY_LOST_AGES_FOLDER).normalize(),
                gameDir.resolve("kubejs").resolve("client_scripts").resolve(LEGACY_LOST_AGES_FOLDER).normalize()
        );

        for (Path obsoleteDirectory : obsoleteDirectories)
        {
            if (!obsoleteDirectory.startsWith(gameDir))
            {
                LOGGER.warn("Skipping obsolete directory outside game directory: {}", obsoleteDirectory);
                continue;
            }

            try
            {
                if (!Files.exists(obsoleteDirectory))
                {
                    LOGGER.debug("Obsolete legacy Lost Ages directory already absent: {}", obsoleteDirectory);
                    continue;
                }

                if (!Files.isDirectory(obsoleteDirectory))
                {
                    LOGGER.warn("Obsolete legacy Lost Ages path exists but is not a directory: {}", obsoleteDirectory);
                    continue;
                }

                try (var entries = Files.list(obsoleteDirectory))
                {
                    if (entries.findAny().isPresent())
                    {
                        LOGGER.warn("Obsolete legacy Lost Ages directory not empty, leaving in place: {}", obsoleteDirectory);
                        continue;
                    }
                }

                Files.delete(obsoleteDirectory);
                LOGGER.info("Removed obsolete empty legacy Lost Ages directory: {}", obsoleteDirectory);
            }
            catch (IOException e)
            {
                LOGGER.error("Failed removing obsolete legacy Lost Ages directory: {}", obsoleteDirectory, e);
            }
        }
    }

    private static List<String> readIndex()
    {
        try (InputStream stream = ManagedFileInstaller.class.getClassLoader().getResourceAsStream(INDEX_RESOURCE))
        {
            if (stream == null)
            {
                throw new RuntimeException("Missing managed file index resource: " + INDEX_RESOURCE);
            }
            return Arrays.asList(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R", -1));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read managed file index: " + INDEX_RESOURCE, e);
        }
    }

    private static byte[] readBundledResource(String resourcePath, int lineNumber)
    {
        try (InputStream stream = ManagedFileInstaller.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (stream == null)
            {
                throw new RuntimeException("Managed resource listed at line " + lineNumber + " not found: " + resourcePath);
            }
            return stream.readAllBytes();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read managed resource at line " + lineNumber + ": " + resourcePath, e);
        }
    }

    private static void copyIfChanged(String resourcePath, Path targetPath, byte[] bundledBytes)
    {
        try
        {
            final Path parent = targetPath.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }

            if (Files.exists(targetPath))
            {
                if (!Files.isRegularFile(targetPath))
                {
                    throw new RuntimeException("Managed target exists but is not a file: " + targetPath);
                }

                final byte[] existing = Files.readAllBytes(targetPath);
                if (Arrays.equals(existing, bundledBytes))
                {
                    LOGGER.info("Managed file unchanged: {}", targetPath);
                    return;
                }
            }

            Files.write(targetPath, bundledBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Managed file copied: {} -> {}", resourcePath, targetPath);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed installing managed file to " + targetPath, e);
        }
    }

    private static void validatePathToken(String token, String label, int lineNumber)
    {
        if (token.isEmpty())
        {
            throw new RuntimeException("Empty " + label + " in managed-files entry at line " + lineNumber);
        }

        if (token.contains(".."))
        {
            throw new RuntimeException("Rejected traversal token in " + label + " at line " + lineNumber + ": " + token);
        }

        if (token.startsWith("/") || token.startsWith("\\") || Paths.get(token).isAbsolute())
        {
            throw new RuntimeException("Rejected absolute " + label + " at line " + lineNumber + ": " + token);
        }
    }

    private static String managedPath(String... parts)
    {
        return String.join("/", parts);
    }
}
