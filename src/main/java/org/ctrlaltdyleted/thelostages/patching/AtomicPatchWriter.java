package org.ctrlaltdyleted.thelostages.patching;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicPatchWriter
{
    private AtomicPatchWriter() {}

    public static void write(Path path, String content) throws IOException
    {
        final Path parent = path.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }

        final Path tempFile = parent != null
                ? Files.createTempFile(parent, path.getFileName().toString(), ".tmp")
                : Files.createTempFile(path.getFileName().toString(), ".tmp");
        try
        {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            if (Files.exists(tempFile))
            {
                Files.deleteIfExists(tempFile);
            }
        }
    }

}
