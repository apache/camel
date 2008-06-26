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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;from/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "from")
@XmlAccessorType(XmlAccessType.FIELD)
public class FromType extends OptionalIdentifiedType<FromType> {
    @XmlAttribute
    private String uri;
    @XmlAttribute
    private String ref;
    @XmlTransient
    private Endpoint<? extends Exchange> endpoint;

    public FromType() {
    }

    public FromType(String uri) {
        setUri(uri);
    }

    public FromType(Endpoint<? extends Exchange> endpoint) {
        this.endpoint = endpoint;
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

    public Endpoint<? extends Exchange> resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            endpoint = context.resolveEndpoint(getUri(), getRef());
        }
        return endpoint;
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
        this.ref = ref;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Returns the endpoint URI or the name of the reference to it
     */
    public Object getUriOrRef() {
        if (ObjectHelper.isNullOrBlank(uri)) {
            return uri;
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
}
