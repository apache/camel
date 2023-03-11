package org.apache.camel.component.zookeeper.cloud;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("meta")
public class MetaData extends HashMap<String, String> {
    public MetaData() {
    }

    public MetaData(Map<? extends String, ? extends String> meta) {
        super(meta);
    }
}
