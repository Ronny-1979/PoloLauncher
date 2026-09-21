package org.mapsforge.map.reader;
import org.mapsforge.core.model.*;
import org.mapsforge.map.datastore.*;
public class MapFile {
    public static volatile Runnable openHook,readHook;
    public static volatile MapReadResult result=new MapReadResult();
    public MapFile(java.io.File file){if(openHook!=null)openHook.run();}
    public BoundingBox boundingBox(){return new BoundingBox();}
    public MapReadResult readMapData(Tile tile){if(readHook!=null)readHook.run();return result;}
    public void close(){}
}
