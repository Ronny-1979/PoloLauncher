package org.openstreetmap.osmosis.core.domain.v0_6;
import java.util.*;
public class Way {
  private final long id; private final java.util.List<WayNode> nodes; private final java.util.Collection<Tag> tags;
  public Way(long id,java.util.List<WayNode> nodes,java.util.Collection<Tag> tags){this.id=id;this.nodes=nodes;this.tags=tags;}
  public long getId(){return id;} public java.util.List<WayNode> getWayNodes(){return nodes;}
  public java.util.Collection<Tag> getTags(){return tags;}
}
