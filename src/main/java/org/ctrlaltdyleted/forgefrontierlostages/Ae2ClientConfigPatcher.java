package org.ctrlaltdyleted.forgefrontierlostages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class Ae2ClientConfigPatcher
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CLIENT_OBJECT = "client";
    private static final String ENABLE_FACADES_IN_JEI = "enableFacadesInJEI";
    private static final String ENABLE_FACADE_RECIPES_IN_JEI = "enableFacadeRecipesInJEI";

    private Ae2ClientConfigPatcher()
    {
    }

    public static void patch()
    {
        final Path configPath = FMLPaths.CONFIGDIR.get().resolve("ae2").resolve("client.json");
        final Path parent = configPath.getParent();

        try
        {
            if (Files.notExists(configPath))
            {
                if (parent != null)
                {
                    Files.createDirectories(parent);
                }

                final JsonObject created = new JsonObject();
                final JsonObject client = new JsonObject();
                client.addProperty(ENABLE_FACADES_IN_JEI, false);
                client.addProperty(ENABLE_FACADE_RECIPES_IN_JEI, false);
                created.add(CLIENT_OBJECT, client);
                writeJson(configPath, created);
                LOGGER.info("AE2 client config created: {}", configPath);
                return;
            }

            final String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            final JsonElement parsed;
            try
            {
                parsed = JsonParser.parseString(raw);
            }
            catch (JsonSyntaxException e)
            {
                throw new RuntimeException("Malformed AE2 client config JSON: " + configPath, e);
            }

            if (!parsed.isJsonObject())
            {
                throw new RuntimeException("Malformed AE2 client config JSON (expected object): " + configPath);
            }

            final JsonObject root = parsed.getAsJsonObject();
            boolean changed = false;

            final JsonObject client;
            if (!root.has(CLIENT_OBJECT))
            {
                client = new JsonObject();
                root.add(CLIENT_OBJECT, client);
                changed = true;
            }
            else
            {
                final JsonElement clientElement = root.get(CLIENT_OBJECT);
                if (!clientElement.isJsonObject())
                {
                    throw new RuntimeException("Malformed AE2 client config JSON ('client' must be an object): " + configPath);
                }
                client = clientElement.getAsJsonObject();
            }

            if (root.has(ENABLE_FACADES_IN_JEI))
            {
                root.remove(ENABLE_FACADES_IN_JEI);
                changed = true;
            }

            if (root.has(ENABLE_FACADE_RECIPES_IN_JEI))
            {
                root.remove(ENABLE_FACADE_RECIPES_IN_JEI);
                changed = true;
            }

            if (!isBooleanFalse(client, ENABLE_FACADES_IN_JEI))
            {
                client.addProperty(ENABLE_FACADES_IN_JEI, false);
                changed = true;
            }

            if (!isBooleanFalse(client, ENABLE_FACADE_RECIPES_IN_JEI))
            {
                client.addProperty(ENABLE_FACADE_RECIPES_IN_JEI, false);
                changed = true;
            }

            if (changed)
            {
                writeJson(configPath, root);
                LOGGER.info("AE2 client config updated: {}", configPath);
            }
            else
            {
                LOGGER.info("AE2 client config unchanged: {}", configPath);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to patch AE2 client config: " + configPath, e);
        }
    }

    private static boolean isBooleanFalse(JsonObject root, String key)
    {
        final JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && !element.getAsBoolean();
    }

    private static void writeJson(Path configPath, JsonObject root) throws IOException
    {
        Files.writeString(
                configPath,
                GSON.toJson(root),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}
