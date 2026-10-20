package org.openstreetmap.osmosis.core.container.v0_6;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
public class NodeContainer extends EntityContainer {
  private final Node entity; public NodeContainer(Node entity){this.entity=entity;} public Node getEntity(){return entity;}
}
