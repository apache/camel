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
package org.apache.camel.spi;

import java.util.Map;

/**
 * Configuration use by {@link org.apache.camel.spi.RestConsumerFactory} for Camel components to support
 * the Camel {@link org.apache.camel.model.rest.RestDefinition rest} DSL.
 */
public class RestConfiguration {

    public enum RestBindingMode {
        auto, off, json, xml, json_xml
    }

    private String component;
    private String scheme;
    private String host;
    private int port;
    private RestBindingMode bindingMode = RestBindingMode.off;
    private String jsonDataFormat;
    private String xmlDataFormat;
    private Map<String, Object> componentProperties;
    private Map<String, Object> endpointProperties;
    private Map<String, Object> consumerProperties;
    private Map<String, Object> dataFormatProperties;

    /**
     * Gets the name of the Camel component to use as the REST consumer
     *
     * @return the component name, or <tt>null</tt> to let Camel search the {@link Registry} to find suitable implementation
     */
    public String getComponent() {
        return component;
    }

    /**
     * Sets the name of the Camel component to use as the REST consumer
     *
     * @param componentName the name of the component (such as restlet, spark-rest, etc.)
     */
    public void setComponent(String componentName) {
        this.component = componentName;
    }

    /**
     * Gets the hostname to use by the REST consumer
     *
     * @return the hostname, or <tt>null</tt> to use default hostname
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the hostname to use by the REST consumer
     *
     * @param host the hostname
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Gets the scheme to use by the REST consumer
     *
     * @return the scheme, or <tt>null</tt> to use default scheme
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Sets the scheme to use by the REST consumer
     *
     * @param scheme the scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Gets the port to use by the REST consumer
     *
     * @return the port, or <tt>0</tt> or <tt>-1</tt> to use default port
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port to use by the REST consumer
     *
     * @param port the port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the binding mode used by the REST consumer
     *
     * @return the binding mode
     */
    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    /**
     * Sets the binding mode to be used by the REST consumer
     *
     * @param bindingMode the binding mode
     */
    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    /**
     * Sets the binding mode to be used by the REST consumer
     *
     * @param bindingMode the binding mode
     */
    public void setBindingMode(String bindingMode) {
        this.bindingMode = RestBindingMode.valueOf(bindingMode);
    }

    /**
     * Gets the name of the json data format.
     *
     * @return the name, or <tt>null</tt> to use default
     */
    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    /**
     * Sets a custom json data format to be used
     *
     * @param name name of the data format
     */
    public void setJsonDataFormat(String name) {
        this.jsonDataFormat = name;
    }

    /**
     * Gets the name of the xml data format.
     *
     * @return the name, or <tt>null</tt> to use default
     */
    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    /**
     * Sets a custom xml data format to be used
     *
     * @param name name of the data format
     */
    public void setXmlDataFormat(String name) {
        this.xmlDataFormat = name;
    }

    /**
     * Gets additional options on component level
     *
     * @return additional options
     */
    public Map<String, Object> getComponentProperties() {
        return componentProperties;
    }

    /**
     * Sets additional options on component level
     *
     * @param componentProperties the options
     */
    public void setComponentProperties(Map<String, Object> componentProperties) {
        this.componentProperties = componentProperties;
    }

    /**
     * Gets additional options on endpoint level
     *
     * @return additional options
     */
    public Map<String, Object> getEndpointProperties() {
        return endpointProperties;
    }

    /**
     * Sets additional options on endpoint level
     *
     * @param endpointProperties the options
     */
    public void setEndpointProperties(Map<String, Object> endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    /**
     * Gets additional options on consumer level
     *
     * @return additional options
     */
    public Map<String, Object> getConsumerProperties() {
        return consumerProperties;
    }

    /**
     * Sets additional options on consumer level
     *
     * @param consumerProperties the options
     */
    public void setConsumerProperties(Map<String, Object> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    /**
     * Gets additional options on data format level
     *
     * @return additional options
     */
    public Map<String, Object> getDataFormatProperties() {
        return dataFormatProperties;
    }

    /**
     * Sets additional options on data format level
     *
     * @param dataFormatProperties the options
     */
    public void setDataFormatProperties(Map<String, Object> dataFormatProperties) {
        this.dataFormatProperties = dataFormatProperties;
    }
}
