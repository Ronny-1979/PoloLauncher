package de.ronny.pololauncher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map files on a USB stick (or any folder): either a directly readable directory or a folder the
 * user picked with the system file picker. Only the known map file names are listed.
 */
final class UsbImportSource {
    interface Entry {
        String name();
        long size();
        long modified();
        InputStream open() throws IOException;
    }

    static final String FILE_PREFIX = "file:";
    static final String TREE_PREFIX = "tree:";

    private final Map<String, Entry> entries;

    private UsbImportSource(Map<String, Entry> entries) { this.entries = entries; }

    Entry find(String name) { return entries.get(name); }

    int count() { return entries.size(); }

    long totalBytes() {
        long sum = 0L;
        for (Entry e : entries.values()) sum += Math.max(0L, e.size());
        return sum;
    }

    /** Opens a source from its saved text form ("file:/path" or "tree:content://..."), or null. */
    static UsbImportSource open(Context context, String spec) {
        if (spec == null) return null;
        try {
            if (spec.startsWith(FILE_PREFIX))
                return new UsbImportSource(scanDirectory(new File(spec.substring(FILE_PREFIX.length()))));
            if (spec.startsWith(TREE_PREFIX))
                return new UsbImportSource(scanTree(context, Uri.parse(spec.substring(TREE_PREFIX.length()))));
        } catch (RuntimeException error) {
            DiagLog.add("USB-Import: Quelle nicht lesbar: " + error.getClass().getSimpleName());
        }
        return null;
    }

    static String fileSpec(File directory) { return FILE_PREFIX + directory.getAbsolutePath(); }

    static String treeSpec(Uri tree) { return TREE_PREFIX + tree; }

    // ---- directories ----

    private static Map<String, Entry> scanDirectory(File dir) {
        Map<String, Entry> result = new LinkedHashMap<>();
        File[] children = dir == null ? null : dir.listFiles();
        if (children == null) return result;
        // The prepared sub folder wins over loose files in the same place.
        for (File child : children) {
            if (child.isDirectory() && UsbImportLayout.isFolderName(child.getName())) {
                result.putAll(scanDirectory(child));
            }
        }
        for (File child : children) {
            if (child.isFile() && UsbImportLayout.isExpected(child.getName(), GermanyOfflineStore.REGION_KEYS)
                    && !result.containsKey(child.getName())) {
                result.put(child.getName(), new FileEntry(child));
            }
        }
        return result;
    }

    private static final class FileEntry implements Entry {
        private final File file;
        FileEntry(File file) { this.file = file; }
        @Override public String name() { return file.getName(); }
        @Override public long size() { return file.length(); }
        @Override public long modified() { return file.lastModified(); }
        @Override public InputStream open() throws IOException { return new FileInputStream(file); }
    }

    /** Readable folders with map files, found without the file picker (removable volumes). */
    static List<File> discoverFolders(Context context) {
        List<File> roots = new ArrayList<>();
        try {
            StorageManager manager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (manager != null && Build.VERSION.SDK_INT >= 30) {
                for (StorageVolume volume : manager.getStorageVolumes()) {
                    File dir = volume.getDirectory();
                    if (dir != null && !volume.isPrimary()) roots.add(dir);
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the plain directory scan
        }
        addChildren(roots, new File("/storage"));
        addChildren(roots, new File("/mnt/media_rw"));
        List<File> found = new ArrayList<>();
        for (File root : roots) {
            if (!containsKnown(found, root) && !scanDirectory(root).isEmpty()) found.add(root);
        }
        return found;
    }

    private static boolean containsKnown(List<File> found, File root) {
        for (File f : found) if (f.getAbsolutePath().equals(root.getAbsolutePath())) return true;
        return false;
    }

    private static void addChildren(List<File> roots, File parent) {
        File[] children = parent.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if ("emulated".equals(name) || "self".equals(name) || !child.isDirectory()) continue;
            boolean duplicate = false;
            for (File r : roots) if (r.getAbsolutePath().equals(child.getAbsolutePath())) duplicate = true;
            if (!duplicate) roots.add(child);
        }
    }

    // ---- system file picker (Storage Access Framework) ----

    private static Map<String, Entry> scanTree(Context context, Uri tree) {
        Map<String, Entry> result = new LinkedHashMap<>();
        ContentResolver resolver = context.getContentResolver();
        scanTreeFolder(resolver, tree, DocumentsContract.getTreeDocumentId(tree), true, result);
        return result;
    }

    private static void scanTreeFolder(ContentResolver resolver, Uri tree, String parentId,
                                       boolean allowDescend, Map<String, Entry> out) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        List<String> subFolders = new ArrayList<>();
        Map<String, Entry> here = new LinkedHashMap<>();
        try (Cursor c = resolver.query(children, columns, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (allowDescend && UsbImportLayout.isFolderName(name)) subFolders.add(id);
                } else if (UsbImportLayout.isExpected(name, GermanyOfflineStore.REGION_KEYS)) {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    here.put(name, new TreeEntry(resolver, document, name,
                            c.isNull(3) ? -1L : c.getLong(3), c.isNull(4) ? 0L : c.getLong(4)));
                }
            }
        } catch (RuntimeException error) {
            DiagLog.add("USB-Import: Ordner nicht lesbar: " + error.getClass().getSimpleName());
            return;
        }
        for (String folder : subFolders) scanTreeFolder(resolver, tree, folder, false, out);
        for (Map.Entry<String, Entry> e : here.entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
    }

    private static final class TreeEntry implements Entry {
        private final ContentResolver resolver;
        private final Uri document;
        private final String name;
        private final long size, modified;
        TreeEntry(ContentResolver resolver, Uri document, String name, long size, long modified) {
            this.resolver = resolver; this.document = document; this.name = name;
            this.size = size; this.modified = modified;
        }
        @Override public String name() { return name; }
        @Override public long size() { return size; }
        @Override public long modified() { return modified; }
        @Override public InputStream open() throws IOException {
            InputStream in = resolver.openInputStream(document);
            if (in == null) throw new IOException("Datei nicht lesbar: " + name);
            return in;
        }
    }
}
