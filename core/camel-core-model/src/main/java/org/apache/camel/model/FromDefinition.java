/*
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
package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Act as a message source as input to a route
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "from")
@XmlAccessorType(XmlAccessType.FIELD)
public class FromDefinition extends OptionalIdentifiedDefinition<FromDefinition> implements EndpointRequiredDefinition {

    @XmlTransient
    private RouteDefinition parent;
    @XmlTransient
    private Endpoint endpoint;
    @XmlTransient
    private EndpointConsumerBuilder endpointConsumerBuilder;

    @XmlAttribute
    @Metadata(required = true, description = "The endpoint URI to consume from.")
    private String uri;
    @XmlAttribute
    @Metadata(description = "To use a variable to store the received message body (only body, not headers)."
                            + " This makes it handy to use variables for user data and to easily control what data to use for sending and receiving.")
    private String variableReceive;

    public FromDefinition() {
    }

    public FromDefinition(String uri) {
        this();
        setUri(uri);
    }

    public FromDefinition(Endpoint endpoint) {
        this();
        setEndpoint(endpoint);
    }

    public FromDefinition(EndpointConsumerBuilder endpointConsumerBuilder) {
        this();
        setEndpointConsumerBuilder(endpointConsumerBuilder);
    }

    FromDefinition copy() {
        FromDefinition copy = new FromDefinition();
        copy.parent = this.parent;
        copy.endpoint = this.endpoint;
        copy.endpointConsumerBuilder = this.endpointConsumerBuilder;
        copy.uri = this.uri;
        copy.variableReceive = this.variableReceive;
        copy.setCamelContext(this.getCamelContext());
        copy.setId(this.getId());
        copy.setCustomId(this.getCustomId());
        copy.setDescription(this.getDescription());
        copy.setNote(this.getNote());
        copy.setLineNumber(this.getLineNumber());
        copy.setLocation(this.getLocation());
        return copy;
    }

    @Override
    public String toString() {
        return "From[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "from";
    }

    @Override
    public String getLabel() {
        String uri = getEndpointUri();
        return uri != null ? uri : "no uri supplied";
    }

    @Override
    public String getEndpointUri() {
        if (uri != null) {
            return uri;
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        } else if (endpointConsumerBuilder != null) {
            return endpointConsumerBuilder.getRawUri();
        } else {
            return null;
        }
    }

    @Override
    public NamedNode getParent() {
        return parent;
    }

    public void setParent(RouteDefinition parent) {
        this.parent = parent;
    }

    // Properties
    // -----------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        clear();
        this.uri = uri;
    }

    public String getVariableReceive() {
        return variableReceive;
    }

    public void setVariableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        clear();
        this.endpoint = endpoint;
    }

    public EndpointConsumerBuilder getEndpointConsumerBuilder() {
        return endpointConsumerBuilder;
    }

    public void setEndpointConsumerBuilder(EndpointConsumerBuilder endpointConsumerBuilder) {
        clear();
        this.endpointConsumerBuilder = endpointConsumerBuilder;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected void clear() {
        this.endpointConsumerBuilder = null;
        this.endpoint = null;
        this.uri = null;
    }

}
