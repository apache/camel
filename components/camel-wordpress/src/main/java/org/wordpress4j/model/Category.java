package org.wordpress4j.model;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Describes a Wordpress Category
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Category extends Classifier implements Serializable {

    private static final long serialVersionUID = 8542893392638055010L;

    private Integer parent;

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

}
