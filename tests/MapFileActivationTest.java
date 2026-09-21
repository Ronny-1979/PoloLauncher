package de.ronny.pololauncher;
import java.io.*;
import java.nio.file.*;
public final class MapFileActivationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static File put(Path dir,String name,String value)throws IOException{Path p=dir.resolve(name);Files.writeString(p,value);return p.toFile();}
    static String read(File file)throws IOException{return Files.readString(file.toPath());}
    static class Disk implements MapFileActivation.Disk {
        boolean rejectActivation, rejectRestore, rejectAtomic=true, rejectBackupDelete;
        public void atomicMove(File a,File b)throws IOException {
            if(rejectAtomic)throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"test");
            Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        public boolean rename(File a,File b){
            if(rejectActivation && a.getName().endsWith(".download"))return false;
            if(rejectRestore && a.getName().endsWith(".backup"))return false;
            return a.renameTo(b);
        }
        public boolean delete(File file){return !(rejectBackupDelete && file.getName().endsWith(".backup")) && file.delete();}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("polo-map-activation-test-");
        try {
            File output=put(root,"region.map","old");File part=put(root,"region.download","new");
            Disk disk=new Disk();
            check(MapFileActivation.activate(part,output,disk)==null,"Fallback activation succeeded");
            check(read(output).equals("new"),"New map installed");
            part=put(root,"region.download","next");disk.rejectActivation=true;
            check(MapFileActivation.activate(part,output,disk)!=null,"Activation failure reported");
            check(read(output).equals("new"),"Rollback restores old map");
            disk.rejectRestore=true;
            check(MapFileActivation.activate(part,output,disk)!=null,"Rollback failure reported");
            File backup=root.resolve("region.map.backup").toFile();
            check(backup.isFile() && read(backup).equals("new"),"Only good backup retained");
            check(MapFileActivation.activate(part,output,disk)!=null,"Second attempt rejects failed restore");
            check(backup.isFile() && read(backup).equals("new"),"Second attempt never deletes only good backup");
            disk.rejectRestore=false;disk.rejectActivation=false;
            check(MapFileActivation.activate(part,output,disk)==null,"Recovered backup then activates download");
            check(read(output).equals("next"),"Recovered transaction correct");
            part=put(root,"region.download","atomic");disk.rejectAtomic=false;
            check(MapFileActivation.activate(part,output,disk)==null && read(output).equals("atomic"),"Atomic replacement succeeds");
            put(root,"region.map.backup","retained");part=put(root,"region.download","unused");
            check(MapFileActivation.activate(part,output,disk)!=null,"Ambiguous backup not overwritten");
            check(read(backup).equals("retained") && read(output).equals("atomic"),"Both existing maps preserved");
            // A stale backup next to a plausible full-size map (left by an earlier run whose backup
            // delete failed) must not block every later update of the region for good.
            try(RandomAccessFile f=new RandomAccessFile(output,"rw")){f.setLength(1_000_000);}
            put(root,"region.map.backup","stale");part=put(root,"region.download","fresh");
            check(MapFileActivation.activate(part,output,disk)==null,"Stale backup no longer blocks an update");
            check(read(output).equals("fresh") && !backup.exists(),"Update applied and stale backup removed");
            // Backup cannot be deleted after a successful replacement: the map IS active, only warn.
            try(RandomAccessFile f=new RandomAccessFile(output,"rw")){f.setLength(1_000_000);}
            part=put(root,"region.download","newest");disk.rejectAtomic=true;disk.rejectBackupDelete=true;
            String warning=MapFileActivation.activate(part,output,disk);
            check(MapFileActivation.activatedWithWarning(warning) && read(output).equals("newest"),"Active map with kept backup is reported as a warning");
            check(!MapFileActivation.activatedWithWarning("Kartendatei konnte nicht aktiviert werden; bisherige Karte bleibt erhalten"),"Real failures are not warnings");
            // The next update repairs the leftover (plausible output, backup removable again).
            disk.rejectBackupDelete=false;
            try(RandomAccessFile f=new RandomAccessFile(output,"rw")){f.setLength(1_000_000);}
            part=put(root,"region.download","after");
            check(MapFileActivation.activate(part,output,disk)==null && read(output).equals("after") && !backup.exists(),"Leftover backup cleaned by the next update");
            System.out.println("MapFileActivationTest: all cases passed");
        } finally {
            try(var paths=Files.walk(root)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}
        }
    }
}
