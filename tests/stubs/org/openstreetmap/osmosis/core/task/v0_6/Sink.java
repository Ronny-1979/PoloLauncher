package org.openstreetmap.osmosis.core.task.v0_6;
import java.util.Map;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
public interface Sink {
  void initialize(Map<String,Object> metaData);
  void process(EntityContainer container);
  void complete();
  void close();
}
