package org.apache.camel.component.stitch.client.models;

import java.util.Map;

public interface StitchModel {

    /**
     * Create a map representation of the model which is essentially the JSON representation of the model.
     *
     * @return {@link Map<String,Object>}
     */
    Map<String, Object> toMap();
}
