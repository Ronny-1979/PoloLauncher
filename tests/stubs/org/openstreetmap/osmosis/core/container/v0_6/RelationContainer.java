package org.openstreetmap.osmosis.core.container.v0_6;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
public final class RelationContainer extends EntityContainer {
    private final Relation entity;
    public RelationContainer(Relation entity){this.entity=entity;}
    public Relation getEntity(){return entity;}
}
