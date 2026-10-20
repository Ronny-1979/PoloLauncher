package org.openstreetmap.osmosis.pbf2.v0_6;
import java.io.File;
import java.util.*;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
public class PbfReader implements Runnable {
  public static java.util.List<EntityContainer> entities = new java.util.ArrayList<>();
  private Sink sink;
  public PbfReader(File file,int workers){}
  public void setSink(Sink sink){this.sink=sink;}
  public void run(){
    sink.initialize(new java.util.HashMap<>());
    for(EntityContainer e:entities)sink.process(e);
    sink.complete(); sink.close();
  }
}
