package org.openstreetmap.osmosis.core.domain.v0_6;
import java.util.*;
public class Node {
  private final long id; private final double lat,lon; private final java.util.Collection<Tag> tags;
  public Node(long id,double lat,double lon,java.util.Collection<Tag> tags){this.id=id;this.lat=lat;this.lon=lon;this.tags=tags;}
  public long getId(){return id;} public double getLatitude(){return lat;} public double getLongitude(){return lon;}
  public java.util.Collection<Tag> getTags(){return tags;}
}
