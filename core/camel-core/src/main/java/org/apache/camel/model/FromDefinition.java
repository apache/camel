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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Act as a message source as input to a route
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "from")
@XmlAccessorType(XmlAccessType.FIELD)
public class FromDefinition extends OptionalIdentifiedDefinition<FromDefinition> implements EndpointRequiredDefinition {
    @XmlAttribute
    @Metadata(required = true)
    private String uri;
    @XmlTransient
    private Endpoint endpoint;
    @XmlTransient
    private EndpointConsumerBuilder endpointConsumerBuilder;

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
            return endpointConsumerBuilder.getUri();
        } else {
            return null;
        }
    }

    // Properties
    // -----------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI of the endpoint to use
     *
     * @param uri the endpoint URI to use
     */
    public void setUri(String uri) {
        clear();
        this.uri = uri;
    }

    /**
     * Gets tne endpoint if an {@link Endpoint} instance was set.
     * <p/>
     * This implementation may return <tt>null</tt> which means you need to use
     * {@link #getEndpointUri()} to get information about the endpoint.
     *
     * @return the endpoint instance, or <tt>null</tt>
     */
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
