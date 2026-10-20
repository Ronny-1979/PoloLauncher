package org.openstreetmap.osmosis.core.domain.v0_6;
public final class RelationMember {
    private final long memberId; private final EntityType memberType; private final String memberRole;
    public RelationMember(long memberId, EntityType memberType, String memberRole) {
        this.memberId=memberId; this.memberType=memberType; this.memberRole=memberRole;
    }
    public long getMemberId(){return memberId;}
    public EntityType getMemberType(){return memberType;}
    public String getMemberRole(){return memberRole;}
}
