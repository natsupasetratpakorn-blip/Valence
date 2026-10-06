package dev.m4sh3r.valence.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One yaml file per team in plugins/Valence/teams.
 */
public final class FileBackend implements Backend {

    private final File folder;

    public FileBackend(File folder) {
        this.folder = folder;
    }

    @Override
    public List<String> loadAll() throws IOException {
        List<String> list = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return list;
        }
        for (File file : files) {
            list.add(Files.readString(file.toPath()));
        }
        return list;
    }

    @Override
    public String load(UUID id) throws IOException {
        File file = new File(folder, id + ".yml");
        return file.exists() ? Files.readString(file.toPath()) : null;
    }

    @Override
    public void save(UUID id, String data) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
        File target = new File(folder, id + ".yml");
        File temp = new File(folder, id + ".yml.tmp");
        Files.writeString(temp.toPath(), data);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void delete(UUID id) throws IOException {
        Files.deleteIfExists(new File(folder, id + ".yml").toPath());
    }

    @Override
    public Map<UUID, String> changes() {
        return Map.of();
    }

    @Override
    public boolean networked() {
        return false;
    }

    @Override
    public boolean lockChest(UUID team) {
        return true;
    }

    @Override
    public void unlockChest(UUID team) {
    }

    @Override
    public void close() {
    }
}
