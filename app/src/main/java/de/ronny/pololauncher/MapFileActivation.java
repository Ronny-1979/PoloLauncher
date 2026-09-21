package de.ronny.pololauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Recoverable map replacement; injectable disk operations support failure tests. */
final class MapFileActivation {
    interface Disk {
        void atomicMove(File source, File target) throws IOException;
        boolean rename(File source, File target);
        boolean delete(File file);
    }
    private static final Disk SYSTEM = new Disk() {
        public void atomicMove(File source, File target) throws IOException {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        public boolean rename(File source, File target) { return source.renameTo(target); }
        public boolean delete(File file) { return file.delete(); }
    };
    /** Same lower bound as {@link OfflineMapStore#installed}: anything smaller is not a usable map. */
    private static final long PLAUSIBLE_MAP_BYTES = 1_000_000L;
    static final String ACTIVE_BUT_BACKUP_KEPT = "Neue Karte ist aktiviert; alte Sicherung konnte nicht entfernt werden";

    private MapFileActivation() {}

    /** True when the message only warns: the new map IS in place, only the old backup remains. */
    static boolean activatedWithWarning(String message) {
        return ACTIVE_BUT_BACKUP_KEPT.equals(message);
    }

    static String activate(File part, File output) { return activate(part, output, SYSTEM); }
    static String activate(File part, File output, Disk disk) {
        File backup = new File(output.getParentFile(), output.getName() + ".backup");
        // A previous failed rollback can leave the only good map in .backup.
        // Restore it before attempting another activation; never delete it here.
        if (backup.exists()) {
            if (output.exists()) {
                // Both files exist only after a run that replaced the map but could not delete its
                // backup: the plausible full-size output is the NEW map, the backup the old one.
                // Removing it unblocks every later update of this region. If the output does not look
                // like a real map (or the delete fails), nothing is touched.
                if (!(output.isFile() && output.length() >= PLAUSIBLE_MAP_BYTES) || !disk.delete(backup))
                    return "Karten-Sicherung vorhanden; sie wird zur Sicherheit nicht überschrieben";
            } else if (!disk.rename(backup, output)) {
                return "Karten-Sicherung konnte nicht wiederhergestellt werden; sie bleibt erhalten";
            }
        }
        try {
            disk.atomicMove(part, output);
            return null;
        } catch (IOException | UnsupportedOperationException failure) {
            // Fall back only while the verified new file is still present.
        }
        if (!part.isFile()) return "Neue Kartendatei nicht mehr verfügbar; bisherige Karte bleibt erhalten";
        boolean hadOutput = output.isFile();
        if (hadOutput && !disk.rename(output, backup)) return "Bestehende Karte kann nicht gesichert werden";
        if (disk.rename(part, output)) {
            if (backup.exists() && !disk.delete(backup))
                return ACTIVE_BUT_BACKUP_KEPT;
            return null;
        }
        if (hadOutput && !disk.rename(backup, output))
            return "Neue Karte nicht aktiviert; Sicherung liegt unter " + backup.getName();
        return "Kartendatei konnte nicht aktiviert werden; bisherige Karte bleibt erhalten";
    }
}
