package top.tobyprime.mcedia_mtv_plugin.channel;

import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-time migration helper: moves channel state from JSON filesystem storage
 * to SQLite.  After a successful migration the old JSON directory is renamed
 * to {@code channels.migrated/} as a backup.
 */
public final class MigrationHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationHelper.class);

    private MigrationHelper() {
    }

    /**
     * Check whether the old JSON storage directory exists and contains data.
     * If so, migrate all states to the given SQLite repository, then rename
     * the directory so the migration runs only once.
     */
    public static void migrateIfNeeded(JavaPlugin plugin, SqLiteChannelRepository sqliteRepo) {
        Path oldDir = plugin.getServer().getWorldContainer().toPath()
                .resolve("plugins")
                .resolve("mtv")
                .resolve("channels");

        if (!Files.isDirectory(oldDir)) {
            return; // nothing to migrate
        }

        // Quick check: any .json files in the directory?
        boolean hasJsonFiles;
        try (var files = Files.list(oldDir)) {
            hasJsonFiles = files.anyMatch(p -> p.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            LOGGER.warn("Failed to list old JSON channel directory, skipping migration: dir={}", oldDir, e);
            return;
        }

        if (!hasJsonFiles) {
            LOGGER.info("Old JSON channel directory exists but is empty, renaming: dir={}", oldDir);
            renameToBackup(oldDir);
            return;
        }

        // Perform migration
        LOGGER.info("Starting migration of MTV channel states from JSON to SQLite");
        var oldRepo = new FileSystemChannelRepository(plugin);
        var states = oldRepo.list();

        if (states.isEmpty()) {
            LOGGER.info("No MTV channel states to migrate, renaming empty directory");
            renameToBackup(oldDir);
            return;
        }

        int migrated = 0;
        int failed = 0;
        for (var state : states) {
            try {
                sqliteRepo.save(state);
                migrated++;
            } catch (Exception e) {
                LOGGER.error("Failed to migrate channel state: channel={}", state.getChannelId(), e);
                failed++;
            }
        }

        if (failed == 0) {
            LOGGER.info("Successfully migrated {} MTV channel states from JSON to SQLite", migrated);
            renameToBackup(oldDir);
        } else {
            LOGGER.warn("Migrated {} channel states with {} failures — old data preserved at: {}",
                    migrated, failed, oldDir);
        }
    }

    private static void renameToBackup(Path oldDir) {
        try {
            var backupDir = oldDir.resolveSibling("channels.migrated");
            // Remove stale backup if it exists
            if (Files.exists(backupDir)) {
                deleteDirectory(backupDir);
            }
            Files.move(oldDir, backupDir, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Renamed old JSON channel directory to: {}", backupDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to rename old JSON channel directory, manual cleanup may be needed: dir={}", oldDir, e);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        try (var files = Files.walk(dir)) {
            for (var file : files.sorted((a, b) -> -a.compareTo(b)).toArray(Path[]::new)) {
                Files.deleteIfExists(file);
            }
        }
    }
}
