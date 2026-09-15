package org.ctrlaltdyleted.forgefrontierlostages.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ResourceVentsConfigPatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_FILENAME = "create_resource_vents.json";
    private static final String VENTS_KEY = "vents";
    private static final String NAME_KEY = "name";

    private static final String CERTUSITE_NAME = "certusite";
    private static final String SKYSTONIUM_NAME = "skystonium";

    private ResourceVentsConfigPatcher()
    {
    }

    public static void patch()
    {
        final Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILENAME);
        if (!Files.exists(configPath))
        {
            LOGGER.warn("{}: Forge Frontier Resource Vents config is missing; skipping Lost Ages Resource Vents patch. Forge Frontier owns the base config and Lost Ages only patches existing vent entries.",
                    configPath.toAbsolutePath().normalize());
            return;
        }

        final JsonObject existingRoot = parseExistingConfig(configPath);
        final JsonObject desiredRoot = buildPatchedRoot(configPath, existingRoot);
        writeIfChanged(configPath, existingRoot, desiredRoot);
    }

    private static JsonObject parseExistingConfig(Path configPath)
    {
        final String text;
        try
        {
            text = Files.readString(configPath, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw invalidState(configPath, "Failed to read configuration file", e);
        }

        final JsonElement parsed = parseJson(configPath, text, "Existing configuration is not valid JSON");
        if (!parsed.isJsonObject())
        {
            throw invalidState(configPath, "Existing configuration root must be a JSON object", null);
        }

        final JsonObject root = parsed.getAsJsonObject();
        if (!root.has(VENTS_KEY))
        {
            throw invalidState(configPath, "Existing configuration is missing required 'vents' property", null);
        }

        final JsonElement vents = root.get(VENTS_KEY);
        if (!vents.isJsonArray())
        {
            throw invalidState(configPath, "Existing configuration 'vents' property must be a JSON array", null);
        }

        return root;
    }

    private static JsonObject buildPatchedRoot(Path configPath, JsonObject existingRoot)
    {
        final JsonObject desired = existingRoot.deepCopy();
        final JsonElement ventsElement = desired.get(VENTS_KEY);
        if (ventsElement == null || !ventsElement.isJsonArray())
        {
            throw invalidState(configPath, "Existing configuration 'vents' property must be a JSON array", null);
        }

        final JsonArray existingVents = ventsElement.getAsJsonArray();
        final JsonArray rebuilt = new JsonArray();

        for (JsonElement entry : existingVents)
        {
            if (entry.isJsonObject())
            {
                final JsonObject obj = entry.getAsJsonObject();
                final JsonElement nameElement = obj.get(NAME_KEY);
                if (nameElement instanceof JsonPrimitive primitive && primitive.isString())
                {
                    final String name = primitive.getAsString();
                    if (CERTUSITE_NAME.equals(name) || SKYSTONIUM_NAME.equals(name))
                    {
                        continue;
                    }
                }
            }
            rebuilt.add(entry);
        }

        desired.add(VENTS_KEY, rebuilt);
        return appendLostAgesVents(desired);
    }

    private static JsonObject appendLostAgesVents(JsonObject root)
    {
        final JsonArray vents = root.getAsJsonArray(VENTS_KEY);
        vents.add(createCertusiteVent());
        vents.add(createSkystoniumVent());
        return root;
    }

    private static JsonObject createCertusiteVent()
    {
        final JsonObject vent = new JsonObject();
        vent.addProperty(NAME_KEY, CERTUSITE_NAME);

        final JsonObject generatedBlock = new JsonObject();
        generatedBlock.addProperty("id", "forgefrontierlostages:certusite");
        generatedBlock.add("properties", new JsonArray());

        final JsonArray generatedBlocks = new JsonArray();
        generatedBlocks.add(generatedBlock);
        vent.add("generatedBlocks", generatedBlocks);

        final JsonArray reactantFluids = new JsonArray();
        reactantFluids.add("create_ethium:echo_compound_fluid");
        vent.add("reactantFluids", reactantFluids);

        vent.addProperty("maxGenerationDistance", 1);
        return vent;
    }

    private static JsonObject createSkystoniumVent()
    {
        final JsonObject vent = new JsonObject();
        vent.addProperty(NAME_KEY, SKYSTONIUM_NAME);

        final JsonObject generatedBlock = new JsonObject();
        generatedBlock.addProperty("id", "forgefrontierlostages:skystonium");
        generatedBlock.add("properties", new JsonArray());

        final JsonArray generatedBlocks = new JsonArray();
        generatedBlocks.add(generatedBlock);
        vent.add("generatedBlocks", generatedBlocks);

        final JsonArray reactantFluids = new JsonArray();
        reactantFluids.add("create_dragons_plus:black_dye");
        vent.add("reactantFluids", reactantFluids);

        vent.addProperty("maxGenerationDistance", 1);
        return vent;
    }

    private static JsonElement parseJson(Path configPath, String text, String message)
    {
        try
        {
            return JsonParser.parseString(text);
        }
        catch (JsonSyntaxException e)
        {
            throw invalidState(configPath, message, e);
        }
    }

    private static void writeIfChanged(Path configPath, JsonObject existing, JsonObject desired)
    {
        if (existing.equals(desired))
        {
            LOGGER.info("Create Resource Vents config already correct: {}", configPath);
            return;
        }

        final Path parent = configPath.getParent();
        try
        {
            if (parent != null)
            {
                Files.createDirectories(parent);
            }

            String serialized = GSON.toJson(desired);
            serialized = serialized.replaceFirst("\\s+$", "") + "\n";

            Files.writeString(
                    configPath,
                    serialized,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        catch (IOException e)
        {
            throw invalidState(configPath, "Failed to write configuration file", e);
        }

        LOGGER.info("Create Resource Vents config patched: {}", configPath);
    }

    private static IllegalStateException invalidState(Path configPath, String message, Exception cause)
    {
        final String fullMessage = configPath.toAbsolutePath().normalize() + ": " + message;
        return cause == null ? new IllegalStateException(fullMessage) : new IllegalStateException(fullMessage, cause);
    }
}
