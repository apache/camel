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
package org.apache.camel.core.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.URISupport;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelEndpointFactoryBean extends AbstractCamelFactoryBean<Endpoint> {
    @XmlAttribute
    @Deprecated
    @Metadata(description = "Not in use")
    private Boolean singleton;
    @XmlAttribute(required = true)
    @Metadata(description = "Sets the URI to use to resolve the endpoint. Notice that additional options can be configured using a series of property.")
    private String uri;
    @XmlAttribute
    @Deprecated
    @Metadata(description = "Sets the exchange pattern of the endpoint")
    private ExchangePattern pattern;
    @XmlElementRef
    @Metadata(description = "To configure additional endpoint options using a XML style which is similar as configuring Spring or Blueprint beans.")
    private List<PropertyDefinition> properties = new ArrayList<PropertyDefinition>();
    @XmlTransient
    private Endpoint endpoint;

    public Endpoint getObject() throws Exception {
        if (endpoint == null || !endpoint.isSingleton()) {
            // resolve placeholders (but leave the original uri unchanged)
            String resolved = getCamelContext().resolvePropertyPlaceholders(uri);
            String target = createUri(resolved);
            this.endpoint = getCamelContext().getEndpoint(target);
            if (endpoint == null) {
                throw new NoSuchEndpointException(target);
            }
        }
        return endpoint;
    }

    public Class<Endpoint> getObjectType() {
        return Endpoint.class;
    }

    @Deprecated
    public Boolean getSingleton() {
        return singleton;
    }

    @Deprecated
    public void setSingleton(Boolean singleton) {
        this.singleton = singleton;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI to use to resolve the endpoint.
     * <p/>
     * Notice that additional options can be configured using a series of property.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    @Deprecated
    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the exchange pattern of the endpoint
     *
     * @deprecated set the pattern in the uri
     */
    @Deprecated
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * To configure additional endpoint options using a XML style which is similar as configuring Spring or Blueprint beans.
     */
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }

    private String createUri(String uri) throws Exception {
        if (properties == null || properties.isEmpty()) {
            return uri;
        } else {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (PropertyDefinition property : properties) {
                // resolve placeholders for each value
                String value = getCamelContext().resolvePropertyPlaceholders(property.getValue());
                map.put(property.getKey(), value);
            }
            return URISupport.appendParametersToURI(uri, map);
        }
    }

}
