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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Required;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Represents an XML &lt;from/&gt; element
 *
 * @version
 */
@XmlRootElement(name = "from")
@XmlAccessorType(XmlAccessType.FIELD)
public class FromDefinition extends OptionalIdentifiedDefinition<FromDefinition> implements EndpointRequiredDefinition {
    @XmlAttribute
    private String uri;
    @XmlAttribute
    private String ref;
    @XmlElementRef
    private List<UriOption> uriOptions; // Only available through XML unmarshalling
    @XmlTransient
    private Endpoint endpoint;

    public FromDefinition() {
    }

    public FromDefinition(String uri) {
        setUri(uri);
    }

    public FromDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    @Override
    public String toString() {
        return "From[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "from";
    }

    public String getLabel() {
        return description(getUri(), getRef(), getEndpoint());
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

    public String getUri() {
        if (uri != null) {
            return ensureUriIsComplete();
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        } else {
            return null;
        }
    }

    /**
     * Sets the URI of the endpoint to use
     *
     * @param uri the endpoint URI to use
     */
    @Required
    public void setUri(String uri) {
        clear();
        this.uri = uri;
    }

    public String getRef() {
        return ref;
    }

    /**
     * Sets the name of the endpoint within the registry (such as the Spring
     * ApplicationContext or JNDI) to use
     *
     * @param ref the reference name to use
     */
    public void setRef(String ref) {
        clear();
        this.ref = ref;
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
        clear();
        this.endpoint = endpoint;
        if (endpoint != null) {
            this.uri = endpoint.getEndpointUri();
        }
    }

    /**
     * Returns the endpoint URI or the name of the reference to it
     */
    public Object getUriOrRef() {
        if (ObjectHelper.isNotEmpty(uri)) {
            return ensureUriIsComplete();
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        }
        return ref;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected static String description(String uri, String ref, Endpoint endpoint) {
        if (ref != null) {
            return "ref:" + ref;
        } else if (endpoint != null) {
            return endpoint.getEndpointUri();
        } else if (uri != null) {
            return uri;
        } else {
            return "no uri or ref supplied!";
        }
    }

    protected void clear() {
        this.endpoint = null;
        this.ref = null;
        this.uri = null;
        this.uriOptions = null;
    }

    protected String ensureUriIsComplete() {
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
}
