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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.Required;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Base class for sending to an endpoint with an optional {@link ExchangePattern}
 *
 * @version
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class SendDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputDefinition<Type> implements EndpointRequiredDefinition {
    @XmlAttribute
    protected String uri;
    @XmlAttribute
    protected String ref;
    @XmlElementRef
    private List<UriOption> uriOptions; // Only available through XML unmarshalling
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
        return getUri();
    }

    // Properties
    // -----------------------------------------------------------------------
    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUri() {
        if (this.uri != null && this.uriOptions != null) {
            try {
                this.uri = URISupport.appendParametersToURI(this.uri, UriOption.transformOptions(this.uriOptions));
            } catch (URISyntaxException e) {
                throw new RuntimeCamelException(String.format("Cannot append uri options to %s", this.uri), e);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeCamelException(String.format("Cannot append uri options to %s", this.uri), e);
            } finally {
                this.uriOptions = null;
            }
        }
        return this.uri;
    }

    @Required
    public void setUri(String uri) {
        this.uri = uri;
        this.uriOptions = null;
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
        this.uriOptions = null;
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
