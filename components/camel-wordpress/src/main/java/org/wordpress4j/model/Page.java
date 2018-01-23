package org.wordpress4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Page extends TextPublishable {

    private static final long serialVersionUID = -3517585398919756299L;
    
    private Integer parent;
    
    @JsonProperty("menu_order")
    private Integer menuOrder;

    public Page() {

    }

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

    public Integer getMenuOrder() {
        return menuOrder;
    }

    public void setMenuOrder(Integer menuOrder) {
        this.menuOrder = menuOrder;
    }
    
}
