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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Sends the message to an endpoint
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class SendDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputDefinition<Type> implements EndpointRequiredDefinition {
    @XmlAttribute @Metadata(required = "true")
    protected String uri;
    @XmlAttribute
    @Deprecated
    protected String ref;
    @XmlTransient
    protected Endpoint endpoint;

    public SendDefinition() {
    }

    public SendDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Endpoint endpoint = resolveEndpoint(routeContext);
        return new SendProcessor(endpoint, getPattern());
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            return context.resolveEndpoint(getUri(), getRef());
        } else {
            return endpoint;
        }
    }

    @Override
    public String getEndpointUri() {
        if (uri != null) {
            return uri;
        }
        return null;
    }

    // Properties
    // -----------------------------------------------------------------------
    public String getRef() {
        return ref;
    }

    /**
     * Sets the reference of the endpoint to send to.
     *
     * @param ref the reference of the endpoint
     * @deprecated use uri with ref:uri instead
     */
    @Deprecated
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Sets the uri of the endpoint to send to.
     *
     * @param uri the uri of the endpoint
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Gets tne endpoint if an {@link Endpoint} instance was set.
     * <p/>
     * This implementation may return <tt>null</tt> which means you need to use
     * {@link #getRef()} or {@link #getUri()} to get information about the endpoint.
     *
     * @return the endpoint instance, or <tt>null</tt>
     */
    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
        this.uri = null;
        if (endpoint != null) {
            this.uri = endpoint.getEndpointUri();
        }
    }

    public ExchangePattern getPattern() {
        return null;
    }

    /**
     * Returns the endpoint URI or the name of the reference to it
     */
    public String getUriOrRef() {
        String uri = getUri();
        if (ObjectHelper.isNotEmpty(uri)) {
            return uri;
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        }
        return getRef();
    }

    @Override
    public String getLabel() {
        return FromDefinition.description(getUri(), getRef(), getEndpoint());
    }
}
