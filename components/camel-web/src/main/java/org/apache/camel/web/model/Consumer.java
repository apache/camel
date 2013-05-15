/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
