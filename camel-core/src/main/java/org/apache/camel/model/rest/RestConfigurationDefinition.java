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
package org.apache.camel.model.rest;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.CamelContextHelper;

/**
 * Represents an XML &lt;restConfiguration/&gt; element
 */
@XmlRootElement(name = "restConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestConfigurationDefinition {

    @XmlAttribute
    private String component;

    @XmlAttribute
    private String host;

    @XmlAttribute
    private String port;

    // TODO: get properties to work with JAXB in the XSD model

//    @XmlElementRef
//    private List<PropertyDefinition> properties = new ArrayList<PropertyDefinition>();

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * To use a specific Camel rest component
     */
    public RestConfigurationDefinition component(String componentId) {
        setComponent(componentId);
        return this;
    }

    public RestConfigurationDefinition host(String host) {
        setHost(host);
        return this;
    }

    public RestConfigurationDefinition port(int port) {
        setPort("" + port);
        return this;
    }

    public RestConfigurationDefinition port(String port) {
        setPort(port);
        return this;
    }

    public RestConfigurationDefinition property(String key, String value) {
        /*PropertyDefinition prop = new PropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getProperties().add(prop);*/
        return this;
    }

    // Implementation
    //-------------------------------------------------------------------------

    /**
     * Creates a {@link org.apache.camel.spi.RestConfiguration} instance based on the definition
     *
     * @param context     the camel context
     * @return the configuration
     * @throws Exception is thrown if error creating the configuration
     */
    public RestConfiguration asRestConfiguration(CamelContext context) throws Exception {
        RestConfiguration answer = new RestConfiguration();
        if (getComponent() != null) {
            answer.setComponent(CamelContextHelper.parseText(context, getComponent()));
        }
        if (getHost() != null) {
            answer.setHost(CamelContextHelper.parseText(context, getHost()));
        }
        if (getPort() != null) {
            answer.setPort(CamelContextHelper.parseInteger(context, getPort()));
        }
        /*if (!properties.isEmpty()) {
            Map<String, Object> props = new HashMap<String, Object>();
            for (PropertyDefinition prop : properties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setProperties(props);
        }*/
        return answer;

    }

}
