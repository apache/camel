package org.apache.camel.web.model;

import org.apache.camel.web.connectors.CamelDataBean;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Consumer {
	
	public static final String PROPERTY_STATUS = "State";
	public static final String PROPERTY_ENDPOINT_URI = "EndpointUri";
	public static final String PROPERTY_ROUTE_ID = "RouteId";

    @XmlAttribute
    private String name;

    private String description;

    private String status;

    private String endpointUri;

    private String routeId;

    public void load(CamelDataBean bean) {
        name = bean.getName();
        description = bean.getDescription();
        status = (String) bean.getProperty(PROPERTY_STATUS);
        endpointUri = (String) bean.getProperty(PROPERTY_ENDPOINT_URI);
        routeId = (String) bean.getProperty(PROPERTY_ROUTE_ID);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
    
    public boolean isStartable() {
        if(!status.equals("Started"))
            return true;
        return false;
    }

    public boolean isStoppable() {
        if(!status.equals("Stopped"))
            return true;
        return false;
    }
}
