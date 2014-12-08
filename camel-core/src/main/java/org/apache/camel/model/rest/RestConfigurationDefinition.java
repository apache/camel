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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
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
    private String scheme;

    @XmlAttribute
    private String host;

    @XmlAttribute
    private String port;

    @XmlAttribute
    private String contextPath;

    @XmlAttribute
    private RestHostNameResolver hostNameResolver;

    @XmlAttribute
    private RestBindingMode bindingMode;

    @XmlAttribute
    private Boolean skipBindingOnErrorCode;

    @XmlAttribute
    private Boolean enableCORS;

    @XmlAttribute
    private String jsonDataFormat;

    @XmlAttribute
    private String xmlDataFormat;

    @XmlElement(name = "componentProperty")
    private List<RestPropertyDefinition> componentProperties = new ArrayList<RestPropertyDefinition>();

    @XmlElement(name = "endpointProperty")
    private List<RestPropertyDefinition> endpointProperties = new ArrayList<RestPropertyDefinition>();

    @XmlElement(name = "consumerProperty")
    private List<RestPropertyDefinition> consumerProperties = new ArrayList<RestPropertyDefinition>();

    @XmlElement(name = "dataFormatProperty")
    private List<RestPropertyDefinition> dataFormatProperties = new ArrayList<RestPropertyDefinition>();

    @XmlElement(name = "corsHeaders")
    private List<RestPropertyDefinition> corsHeaders = new ArrayList<RestPropertyDefinition>();

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
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

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public RestHostNameResolver getHostNameResolver() {
        return hostNameResolver;
    }

    public void setHostNameResolver(RestHostNameResolver hostNameResolver) {
        this.hostNameResolver = hostNameResolver;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public Boolean getEnableCORS() {
        return enableCORS;
    }

    public void setEnableCORS(Boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    public void setJsonDataFormat(String jsonDataFormat) {
        this.jsonDataFormat = jsonDataFormat;
    }

    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    public void setXmlDataFormat(String xmlDataFormat) {
        this.xmlDataFormat = xmlDataFormat;
    }

    public List<RestPropertyDefinition> getComponentProperties() {
        return componentProperties;
    }

    public void setComponentProperties(List<RestPropertyDefinition> componentProperties) {
        this.componentProperties = componentProperties;
    }

    public List<RestPropertyDefinition> getEndpointProperties() {
        return endpointProperties;
    }

    public void setEndpointProperties(List<RestPropertyDefinition> endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public List<RestPropertyDefinition> getConsumerProperties() {
        return consumerProperties;
    }

    public void setConsumerProperties(List<RestPropertyDefinition> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public List<RestPropertyDefinition> getDataFormatProperties() {
        return dataFormatProperties;
    }

    public void setDataFormatProperties(List<RestPropertyDefinition> dataFormatProperties) {
        this.dataFormatProperties = dataFormatProperties;
    }

    public List<RestPropertyDefinition> getCorsHeaders() {
        return corsHeaders;
    }

    public void setCorsHeaders(List<RestPropertyDefinition> corsHeaders) {
        this.corsHeaders = corsHeaders;
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

    /**
     * To use a specific scheme such as http/https
     */
    public RestConfigurationDefinition scheme(String scheme) {
        setScheme(scheme);
        return this;
    }

    /**
     * To define the host to use, such as 0.0.0.0 or localhost
     */
    public RestConfigurationDefinition host(String host) {
        setHost(host);
        return this;
    }

    /**
     * To specify the port number to use for the REST service
     */
    public RestConfigurationDefinition port(int port) {
        setPort("" + port);
        return this;
    }

    /**
     * To specify the port number to use for the REST service
     */
    public RestConfigurationDefinition port(String port) {
        setPort(port);
        return this;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application
     * is deployed using a context-path.
     */
    public RestConfigurationDefinition contextPath(String contextPath) {
        setContextPath(contextPath);
        return this;
    }

    /**
     * To specify the hostname resolver
     */
    public RestConfigurationDefinition hostNameResolver(RestHostNameResolver hostNameResolver) {
        setHostNameResolver(hostNameResolver);
        return this;
    }

    /**
     * To specify the binding mode
     */
    public RestConfigurationDefinition bindingMode(RestBindingMode bindingMode) {
        setBindingMode(bindingMode);
        return this;
    }

    /**
     * To specify whether to skip binding output if there is a custom HTTP error code
     */
    public RestConfigurationDefinition skipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        return this;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic include CORS in the HTTP headers in the response.
     */
    public RestConfigurationDefinition enableCORS(boolean enableCORS) {
        setEnableCORS(enableCORS);
        return this;
    }

    /**
     * To use a specific json data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
     *
     * @param name  name of the data format to {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String) resolve}
     */
    public RestConfigurationDefinition jsonDataFormat(String name) {
        setJsonDataFormat(name);
        return this;
    }

    /**
     * To use a specific XML data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
     *
     * @param name  name of the data format to {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String) resolve}
     */
    public RestConfigurationDefinition xmlDataFormat(String name) {
        setXmlDataFormat(name);
        return this;
    }

    /**
     * For additional configuration options on component level
     */
    public RestConfigurationDefinition componentProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getComponentProperties().add(prop);
        return this;
    }

    /**
     * For additional configuration options on endpoint level
     */
    public RestConfigurationDefinition endpointProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getEndpointProperties().add(prop);
        return this;
    }

    /**
     * For additional configuration options on consumer level
     */
    public RestConfigurationDefinition consumerProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getConsumerProperties().add(prop);
        return this;
    }

    /**
     * For additional configuration options on data format level
     */
    public RestConfigurationDefinition dataFormatProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getDataFormatProperties().add(prop);
        return this;
    }

    /**
     * For configuring CORS headers
     */
    public RestConfigurationDefinition corsHeaderProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getCorsHeaders().add(prop);
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
        if (component != null) {
            answer.setComponent(CamelContextHelper.parseText(context, component));
        }
        if (scheme != null) {
            answer.setScheme(CamelContextHelper.parseText(context, scheme));
        }
        if (host != null) {
            answer.setHost(CamelContextHelper.parseText(context, host));
        }
        if (port != null) {
            answer.setPort(CamelContextHelper.parseInteger(context, port));
        }
        if (contextPath != null) {
            answer.setContextPath(CamelContextHelper.parseText(context, contextPath));
        }
        if (hostNameResolver != null) {
            answer.setRestHostNameResolver(hostNameResolver.name());
        }
        if (bindingMode != null) {
            answer.setBindingMode(bindingMode.name());
        }
        if (skipBindingOnErrorCode != null) {
            answer.setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        }
        if (enableCORS != null) {
            answer.setEnableCORS(enableCORS);
        }
        if (jsonDataFormat != null) {
            answer.setJsonDataFormat(jsonDataFormat);
        }
        if (xmlDataFormat != null) {
            answer.setXmlDataFormat(xmlDataFormat);
        }
        if (!componentProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<String, Object>();
            for (RestPropertyDefinition prop : componentProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setComponentProperties(props);
        }
        if (!endpointProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<String, Object>();
            for (RestPropertyDefinition prop : endpointProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setEndpointProperties(props);
        }
        if (!consumerProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<String, Object>();
            for (RestPropertyDefinition prop : consumerProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setConsumerProperties(props);
        }
        if (!dataFormatProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<String, Object>();
            for (RestPropertyDefinition prop : dataFormatProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setDataFormatProperties(props);
        }
        if (!corsHeaders.isEmpty()) {
            Map<String, String> props = new HashMap<String, String>();
            for (RestPropertyDefinition prop : corsHeaders) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            answer.setCorsHeaders(props);
        }
        return answer;
    }

}
