package org.openstreetmap.osmosis.core.domain.v0_6;
public class Tag {
  private final String key,value;
  public Tag(String key,String value){this.key=key;this.value=value;}
  public String getKey(){return key;}
  public String getValue(){return value;}
}
