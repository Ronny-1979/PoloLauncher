package org.openstreetmap.osmosis.core.domain.v0_6;
import java.util.*;
public final class Relation {
    private final long id; private final List<RelationMember> members; private final Collection<Tag> tags;
    public Relation(long id,List<RelationMember> members,Collection<Tag> tags){this.id=id;this.members=members;this.tags=tags;}
    public long getId(){return id;}
    public List<RelationMember> getMembers(){return members;}
    public Collection<Tag> getTags(){return tags;}
}
