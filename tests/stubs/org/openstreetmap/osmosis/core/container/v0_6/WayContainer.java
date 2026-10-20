package org.openstreetmap.osmosis.core.container.v0_6;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
public class WayContainer extends EntityContainer {
  private final Way entity; public WayContainer(Way entity){this.entity=entity;} public Way getEntity(){return entity;}
}
