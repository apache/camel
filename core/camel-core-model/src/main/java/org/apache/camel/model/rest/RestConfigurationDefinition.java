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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;

/**
 * To configure rest
 */
@Metadata(label = "rest")
@XmlRootElement(name = "restConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestConfigurationDefinition {

    @XmlAttribute
    @Metadata(enums = "platform-http,servlet,jetty,undertow,netty-http,coap")
    private String component;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", enums = "openapi,swagger")
    private String apiComponent;
    @XmlAttribute
    @Metadata(label = "producer,advanced", enums = "vertx-http,http,undertow,netty-http")
    private String producerComponent;
    @XmlAttribute
    private String scheme;
    @XmlAttribute
    private String host;
    @XmlAttribute
    private String port;
    @XmlAttribute
    @Metadata(label = "consumer,advanced")
    private String apiHost;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String useXForwardHeaders;
    @XmlAttribute
    @Metadata(label = "producer,advanced")
    private String producerApiDoc;
    @XmlAttribute
    @Metadata(label = "consumer")
    private String contextPath;
    @XmlAttribute
    @Metadata(label = "consumer")
    private String apiContextPath;
    @XmlAttribute
    @Metadata(label = "consumer,advanced")
    private String apiContextRouteId;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String apiVendorExtension;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", defaultValue = "allLocalIp")
    private RestHostNameResolver hostNameResolver;
    @XmlAttribute
    @Metadata(defaultValue = "off", enums = "off,auto,json,xml,json_xml")
    private RestBindingMode bindingMode;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String skipBindingOnErrorCode;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String clientRequestValidation;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableCORS;
    @XmlAttribute
    @Metadata(label = "consumer,advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableNoContentResponse;
    @XmlAttribute
    @Metadata(label = "consumer", javaType = "java.lang.Boolean", defaultValue = "false")
    private String inlineRoutes;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String jsonDataFormat;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String xmlDataFormat;
    @XmlElement(name = "componentProperty")
    @Metadata(label = "advanced")
    private List<RestPropertyDefinition> componentProperties = new ArrayList<>();
    @XmlElement(name = "endpointProperty")
    @Metadata(label = "advanced")
    private List<RestPropertyDefinition> endpointProperties = new ArrayList<>();
    @XmlElement(name = "consumerProperty")
    @Metadata(label = "consumer,advanced")
    private List<RestPropertyDefinition> consumerProperties = new ArrayList<>();
    @XmlElement(name = "dataFormatProperty")
    @Metadata(label = "advanced")
    private List<RestPropertyDefinition> dataFormatProperties = new ArrayList<>();
    @XmlElement(name = "apiProperty")
    @Metadata(label = "consumer,advanced")
    private List<RestPropertyDefinition> apiProperties = new ArrayList<>();
    @XmlElement(name = "corsHeaders")
    @Metadata(label = "consumer,advanced")
    private List<RestPropertyDefinition> corsHeaders = new ArrayList<>();

    public String getComponent() {
        return component;
    }

    /**
     * The Camel Rest component to use for the REST transport (consumer), such as netty-http, jetty, servlet, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component that
     * integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry. If
     * either one is found, then that is being used.
     */
    public void setComponent(String component) {
        this.component = component;
    }

    public String getApiComponent() {
        return apiComponent;
    }

    /**
     * The name of the Camel component to use as the REST API. If no API Component has been explicit configured, then
     * Camel will lookup if there is a Camel component responsible for servicing and generating the REST API
     * documentation, or if a org.apache.camel.spi.RestApiProcessorFactory is registered in the registry. If either one
     * is found, then that is being used.
     */
    public void setApiComponent(String apiComponent) {
        this.apiComponent = apiComponent;
    }

    public String getProducerComponent() {
        return producerComponent;
    }

    /**
     * Sets the name of the Camel component to use as the REST producer
     */
    public void setProducerComponent(String producerComponent) {
        this.producerComponent = producerComponent;
    }

    public String getScheme() {
        return scheme;
    }

    /**
     * The scheme to use for exposing the REST service. Usually http or https is supported.
     * <p/>
     * The default value is http
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    /**
     * The hostname to use for exposing the REST service.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public String getApiHost() {
        return apiHost;
    }

    /**
     * To use a specific hostname for the API documentation (such as swagger or openapi)
     * <p/>
     * This can be used to override the generated host with this configured hostname
     */
    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getPort() {
        return port;
    }

    /**
     * The port number to use for exposing the REST service. Notice if you use servlet component then the port number
     * configured here does not apply, as the port number in use is the actual port number the servlet component is
     * using. eg if using Apache Tomcat its the tomcat http port, if using Apache Karaf its the HTTP service in Karaf
     * that uses port 8181 by default etc. Though in those situations setting the port number here, allows tooling and
     * JMX to know the port number, so its recommended to set the port number to the number that the servlet engine
     * uses.
     */
    public void setPort(String port) {
        this.port = port;
    }

    public String getProducerApiDoc() {
        return producerApiDoc;
    }

    /**
     * Sets the location of the api document the REST producer will use to validate the REST uri and query parameters
     * are valid accordingly to the api document.
     * <p/>
     * The location of the api document is loaded from classpath by default, but you can use <tt>file:</tt> or
     * <tt>http:</tt> to refer to resources to load from file or http url.
     */
    public void setProducerApiDoc(String producerApiDoc) {
        this.producerApiDoc = producerApiDoc;
    }

    public String getContextPath() {
        return contextPath;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application is
     * deployed using a context-path. Or for components such as <tt>camel-jetty</tt> or <tt>camel-netty-http</tt> that
     * includes a HTTP server.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    /**
     * Sets a leading API context-path the REST API services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application is
     * deployed using a context-path.
     *
     * @param contextPath the API context path
     */
    public void setApiContextPath(String contextPath) {
        this.apiContextPath = contextPath;
    }

    public String getApiContextRouteId() {
        return apiContextRouteId;
    }

    /**
     * Sets the route id to use for the route that services the REST API.
     * <p/>
     * The route will by default use an auto assigned route id.
     *
     * @param apiContextRouteId the route id
     */
    public void setApiContextRouteId(String apiContextRouteId) {
        this.apiContextRouteId = apiContextRouteId;
    }

    public String getApiVendorExtension() {
        return apiVendorExtension;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then Camel will include additional information
     * as vendor extension (eg keys starting with x-) such as route ids, class names etc. Not all 3rd party API gateways
     * and tools supports vendor-extensions when importing your API docs.
     */
    public void setApiVendorExtension(String apiVendorExtension) {
        this.apiVendorExtension = apiVendorExtension;
    }

    public RestHostNameResolver getHostNameResolver() {
        return hostNameResolver;
    }

    /**
     * If no hostname has been explicit configured, then this resolver is used to compute the hostname the REST service
     * will be using.
     */
    public void setHostNameResolver(RestHostNameResolver hostNameResolver) {
        this.hostNameResolver = hostNameResolver;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    /**
     * Sets the binding mode to use.
     * <p/>
     * The default value is off
     */
    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding on output if there is a custom HTTP error code header. This allows to build custom error
     * messages that do not bind to json / xml etc, as success messages otherwise will do.
     */
    public void setSkipBindingOnErrorCode(String skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public String getClientRequestValidation() {
        return clientRequestValidation;
    }

    /**
     * Whether to enable validation of the client request to check:
     *
     * 1) Content-Type header matches what the Rest DSL consumes; returns HTTP Status 415 if validation error. 2) Accept
     * header matches what the Rest DSL produces; returns HTTP Status 406 if validation error. 3) Missing required data
     * (query parameters, HTTP headers, body); returns HTTP Status 400 if validation error. 4) Parsing error of the
     * message body (JSon, XML or Auto binding mode must be enabled); returns HTTP Status 400 if validation error.
     */
    public void setClientRequestValidation(String clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public String getEnableCORS() {
        return enableCORS;
    }

    /**
     * Whether to enable CORS headers in the HTTP response.
     * <p/>
     * The default value is false.
     */
    public void setEnableCORS(String enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getEnableNoContentResponse() {
        return enableNoContentResponse;
    }

    /**
     * Whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML root object.
     * <p/>
     * The default value is false.
     */
    public void setEnableNoContentResponse(String enableNoContentResponse) {
        this.enableNoContentResponse = enableNoContentResponse;
    }

    public String getInlineRoutes() {
        return inlineRoutes;
    }

    /**
     * Inline routes in rest-dsl which are linked using direct endpoints.
     *
     * By default, each service in Rest DSL is an individual route, meaning that you would have at least two routes per
     * service (rest-dsl, and the route linked from rest-dsl). Enabling this allows Camel to optimize and inline this as
     * a single route, however this requires to use direct endpoints, which must be unique per service.
     *
     * This option is default <tt>false</tt>.
     */
    public void setInlineRoutes(String inlineRoutes) {
        this.inlineRoutes = inlineRoutes;
    }

    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    /**
     * Name of specific json data format to use. By default jackson will be used. Important: This option is only for
     * setting a custom name of the data format, not to refer to an existing data format instance.
     */
    public void setJsonDataFormat(String jsonDataFormat) {
        this.jsonDataFormat = jsonDataFormat;
    }

    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    /**
     * Name of specific XML data format to use. By default jaxb will be used. Important: This option is only for setting
     * a custom name of the data format, not to refer to an existing data format instance.
     */
    public void setXmlDataFormat(String xmlDataFormat) {
        this.xmlDataFormat = xmlDataFormat;
    }

    public List<RestPropertyDefinition> getComponentProperties() {
        return componentProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest component in use.
     */
    public void setComponentProperties(List<RestPropertyDefinition> componentProperties) {
        this.componentProperties = componentProperties;
    }

    public List<RestPropertyDefinition> getEndpointProperties() {
        return endpointProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest endpoint in use.
     */
    public void setEndpointProperties(List<RestPropertyDefinition> endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public List<RestPropertyDefinition> getConsumerProperties() {
        return consumerProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest consumer in use.
     */
    public void setConsumerProperties(List<RestPropertyDefinition> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public List<RestPropertyDefinition> getDataFormatProperties() {
        return dataFormatProperties;
    }

    /**
     * Allows to configure as many additional properties for the data formats in use. For example set property
     * prettyPrint to true to have json outputted in pretty mode. The properties can be prefixed to denote the option is
     * only for either JSON or XML and for either the IN or the OUT. The prefixes are:
     * <ul>
     * <li>json.in.</li>
     * <li>json.out.</li>
     * <li>xml.in.</li>
     * <li>xml.out.</li>
     * </ul>
     * For example a key with value "xml.out.mustBeJAXBElement" is only for the XML data format for the outgoing. A key
     * without a prefix is a common key for all situations.
     */
    public void setDataFormatProperties(List<RestPropertyDefinition> dataFormatProperties) {
        this.dataFormatProperties = dataFormatProperties;
    }

    public List<RestPropertyDefinition> getApiProperties() {
        return apiProperties;
    }

    /**
     * Allows to configure as many additional properties for the api documentation. For example set property api.title
     * to my cool stuff
     */
    public void setApiProperties(List<RestPropertyDefinition> apiProperties) {
        this.apiProperties = apiProperties;
    }

    public List<RestPropertyDefinition> getCorsHeaders() {
        return corsHeaders;
    }

    /**
     * Allows to configure custom CORS headers.
     */
    public void setCorsHeaders(List<RestPropertyDefinition> corsHeaders) {
        this.corsHeaders = corsHeaders;
    }

    public String getUseXForwardHeaders() {
        return useXForwardHeaders;
    }

    /**
     * Whether to use X-Forward headers for Host and related setting.
     * <p/>
     * The default value is true.
     */
    public void setUseXForwardHeaders(String useXForwardHeaders) {
        this.useXForwardHeaders = useXForwardHeaders;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * To use a specific Camel rest component (consumer)
     */
    public RestConfigurationDefinition component(String componentId) {
        setComponent(componentId);
        return this;
    }

    /**
     * To use a specific Camel rest API component
     */
    public RestConfigurationDefinition apiComponent(String componentId) {
        setApiComponent(componentId);
        return this;
    }

    /**
     * To use a specific Camel rest component (producer)
     */
    public RestConfigurationDefinition producerComponent(String componentId) {
        setProducerComponent(componentId);
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
     * To define a specific host to use for API documentation instead of using a generated API hostname that is relative
     * to the REST service host.
     */
    public RestConfigurationDefinition apiHost(String host) {
        setApiHost(host);
        return this;
    }

    /**
     * To specify the port number to use for the REST service
     */
    public RestConfigurationDefinition port(int port) {
        setPort(Integer.toString(port));
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
     * Sets the location of the api document the REST producer will use to validate the REST uri and query parameters
     * are valid accordingly to the api document.
     * <p/>
     * The location of the api document is loaded from classpath by default, but you can use <tt>file:</tt> or
     * <tt>http:</tt> to refer to resources to load from file or http url.
     */
    public RestConfigurationDefinition producerApiDoc(String apiDoc) {
        setProducerApiDoc(apiDoc);
        return this;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application is
     * deployed using a context-path. Or for components such as <tt>camel-jetty</tt> or <tt>camel-netty-http</tt> that
     * includes a HTTP server.
     */
    public RestConfigurationDefinition apiContextPath(String contextPath) {
        setApiContextPath(contextPath);
        return this;
    }

    /**
     * Sets the route id to use for the route that services the REST API.
     * <p/>
     * The route will by default use an auto assigned route id.
     */
    public RestConfigurationDefinition apiContextRouteId(String apiContextRouteId) {
        setApiContextRouteId(apiContextRouteId);
        return this;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then Camel will include additional information
     * as vendor extension (eg keys starting with x-) such as route ids, class names etc. Some API tooling may not
     * support vendor extensions and this option can then be turned off.
     */
    public RestConfigurationDefinition apiVendorExtension(boolean vendorExtension) {
        setApiVendorExtension(vendorExtension ? "true" : "false");
        return this;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then Camel will include additional information
     * as vendor extension (eg keys starting with x-) such as route ids, class names etc. Some API tooling may not
     * support vendor extensions and this option can then be turned off.
     */
    public RestConfigurationDefinition apiVendorExtension(String vendorExtension) {
        setApiVendorExtension(vendorExtension);
        return this;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application is
     * deployed using a context-path.
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
     * To specify the binding mode
     */
    public RestConfigurationDefinition bindingMode(String bindingMode) {
        setBindingMode(RestBindingMode.valueOf(bindingMode.toLowerCase()));
        return this;
    }

    /**
     * To specify whether to skip binding output if there is a custom HTTP error code
     */
    public RestConfigurationDefinition skipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        setSkipBindingOnErrorCode(skipBindingOnErrorCode ? "true" : "false");
        return this;
    }

    /**
     * To specify whether to skip binding output if there is a custom HTTP error code
     */
    public RestConfigurationDefinition skipBindingOnErrorCode(String skipBindingOnErrorCode) {
        setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        return this;
    }

    /**
     * Whether to enable validation of the client request to check:
     *
     * 1) Content-Type header matches what the Rest DSL consumes; returns HTTP Status 415 if validation error. 2) Accept
     * header matches what the Rest DSL produces; returns HTTP Status 406 if validation error. 3) Missing required data
     * (query parameters, HTTP headers, body); returns HTTP Status 400 if validation error. 4) Parsing error of the
     * message body (JSon, XML or Auto binding mode must be enabled); returns HTTP Status 400 if validation error.
     */
    public RestConfigurationDefinition clientRequestValidation(boolean clientRequestValidation) {
        setClientRequestValidation(clientRequestValidation ? "true" : "false");
        return this;
    }

    /**
     * Whether to enable validation of the client request to check:
     *
     * 1) Content-Type header matches what the Rest DSL consumes; returns HTTP Status 415 if validation error. 2) Accept
     * header matches what the Rest DSL produces; returns HTTP Status 406 if validation error. 3) Missing required data
     * (query parameters, HTTP headers, body); returns HTTP Status 400 if validation error. 4) Parsing error of the
     * message body (JSon, XML or Auto binding mode must be enabled); returns HTTP Status 400 if validation error.
     */
    public RestConfigurationDefinition clientRequestValidation(String clientRequestValidation) {
        setClientRequestValidation(clientRequestValidation);
        return this;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic include CORS in the HTTP headers in the
     * response.
     */
    public RestConfigurationDefinition enableCORS(boolean enableCORS) {
        setEnableCORS(enableCORS ? "true" : "false");
        return this;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic include CORS in the HTTP headers in the
     * response.
     */
    public RestConfigurationDefinition enableCORS(String enableCORS) {
        setEnableCORS(enableCORS);
        return this;
    }

    /**
     * To Specify whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML
     * root object.
     */
    public RestConfigurationDefinition enableNoContentResponse(boolean enableNoContentResponse) {
        setEnableNoContentResponse(enableNoContentResponse ? "true" : "false");
        return this;
    }

    /**
     * To specify whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML
     * root object.
     */
    public RestConfigurationDefinition enableNoContentResponse(String enableNoContentResponse) {
        setEnableNoContentResponse(enableNoContentResponse);
        return this;
    }

    /**
     * Inline routes in rest-dsl which are linked using direct endpoints.
     *
     * By default, each service in Rest DSL is an individual route, meaning that you would have at least two routes per
     * service (rest-dsl, and the route linked from rest-dsl). Enabling this allows Camel to optimize and inline this as
     * a single route, however this requires to use direct endpoints, which must be unique per service.
     *
     * This option is default <tt>false</tt>.
     */
    public RestConfigurationDefinition inlineRoutes(String inlineRoutes) {
        setInlineRoutes(inlineRoutes);
        return this;
    }

    /**
     * Inline routes in rest-dsl which are linked using direct endpoints.
     *
     * By default, each service in Rest DSL is an individual route, meaning that you would have at least two routes per
     * service (rest-dsl, and the route linked from rest-dsl). Enabling this allows Camel to optimize and inline this as
     * a single route, however this requires to use direct endpoints, which must be unique per service.
     *
     * This option is default <tt>false</tt>.
     */
    public RestConfigurationDefinition inlineRoutes(boolean inlineRoutes) {
        setInlineRoutes(inlineRoutes ? "true" : "false");
        return this;
    }

    /**
     * To use a specific json data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing
     * data format instance.
     *
     * @param name name of the data format to {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String)
     *             resolve}
     */
    public RestConfigurationDefinition jsonDataFormat(String name) {
        setJsonDataFormat(name);
        return this;
    }

    /**
     * To use a specific XML data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing
     * data format instance.
     *
     * @param name name of the data format to {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String)
     *             resolve}
     */
    public RestConfigurationDefinition xmlDataFormat(String name) {
        setXmlDataFormat(name);
        return this;
    }

    /**
     * For additional configuration options on component level
     * <p/>
     * The value can use <tt>#</tt> to refer to a bean to lookup in the registry.
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
     * <p/>
     * The value can use <tt>#</tt> to refer to a bean to lookup in the registry.
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
     * <p/>
     * The value can use <tt>#</tt> to refer to a bean to lookup in the registry.
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
     * <p/>
     * The value can use <tt>#</tt> to refer to a bean to lookup in the registry.
     */
    public RestConfigurationDefinition dataFormatProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getDataFormatProperties().add(prop);
        return this;
    }

    /**
     * For configuring an api property, such as <tt>api.title</tt>, or <tt>api.version</tt>.
     */
    public RestConfigurationDefinition apiProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getApiProperties().add(prop);
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

    /**
     * Shortcut for setting the {@code Access-Control-Allow-Credentials} header.
     */
    public RestConfigurationDefinition corsAllowCredentials(boolean corsAllowCredentials) {
        return corsHeaderProperty("Access-Control-Allow-Credentials", String.valueOf(corsAllowCredentials));
    }

    /**
     * To specify whether to use X-Forward headers for Host and related setting
     */
    public RestConfigurationDefinition useXForwardHeaders(boolean useXForwardHeaders) {
        setUseXForwardHeaders(useXForwardHeaders ? "true" : "false");
        return this;
    }

    /**
     * To specify whether to use X-Forward headers for Host and related setting
     */
    public RestConfigurationDefinition useXForwardHeaders(String useXForwardHeaders) {
        setUseXForwardHeaders(useXForwardHeaders);
        return this;
    }

    // Implementation
    // -------------------------------------------------------------------------

    /**
     * Configured an instance of a {@link org.apache.camel.spi.RestConfiguration} instance based on the definition
     *
     * @param  context   the camel context
     * @param  target    the {@link org.apache.camel.spi.RestConfiguration} target
     * @return           the configuration
     * @throws Exception is thrown if error creating the configuration
     */
    public RestConfiguration asRestConfiguration(CamelContext context, RestConfiguration target) throws Exception {
        if (component != null) {
            target.setComponent(CamelContextHelper.parseText(context, component));
        }
        if (apiComponent != null) {
            target.setApiComponent(CamelContextHelper.parseText(context, apiComponent));
        }
        if (producerComponent != null) {
            target.setProducerComponent(CamelContextHelper.parseText(context, producerComponent));
        }
        if (scheme != null) {
            target.setScheme(CamelContextHelper.parseText(context, scheme));
        }
        if (host != null) {
            target.setHost(CamelContextHelper.parseText(context, host));
        }
        if (useXForwardHeaders != null) {
            target.setUseXForwardHeaders(CamelContextHelper.parseBoolean(context, useXForwardHeaders));
        }
        if (apiHost != null) {
            target.setApiHost(CamelContextHelper.parseText(context, apiHost));
        }
        if (port != null) {
            target.setPort(CamelContextHelper.parseInteger(context, port));
        }
        if (producerApiDoc != null) {
            target.setProducerApiDoc(CamelContextHelper.parseText(context, producerApiDoc));
        }
        if (apiContextPath != null) {
            target.setApiContextPath(CamelContextHelper.parseText(context, apiContextPath));
        }
        if (apiContextRouteId != null) {
            target.setApiContextRouteId(CamelContextHelper.parseText(context, apiContextRouteId));
        }
        if (apiVendorExtension != null) {
            target.setApiVendorExtension(CamelContextHelper.parseBoolean(context, apiVendorExtension));
        }
        if (contextPath != null) {
            target.setContextPath(CamelContextHelper.parseText(context, contextPath));
        }
        if (hostNameResolver != null) {
            target.setHostNameResolver(hostNameResolver.name());
        }
        if (bindingMode != null) {
            target.setBindingMode(bindingMode.name());
        }
        if (skipBindingOnErrorCode != null) {
            target.setSkipBindingOnErrorCode(CamelContextHelper.parseBoolean(context, skipBindingOnErrorCode));
        }
        if (clientRequestValidation != null) {
            target.setClientRequestValidation(CamelContextHelper.parseBoolean(context, clientRequestValidation));
        }
        if (enableCORS != null) {
            target.setEnableCORS(CamelContextHelper.parseBoolean(context, enableCORS));
        }
        if (enableNoContentResponse != null) {
            target.setEnableNoContentResponse(CamelContextHelper.parseBoolean(context, enableNoContentResponse));
        }
        if (inlineRoutes != null) {
            target.setInlineRoutes(CamelContextHelper.parseBoolean(context, inlineRoutes));
        }
        if (jsonDataFormat != null) {
            target.setJsonDataFormat(jsonDataFormat);
        }
        if (xmlDataFormat != null) {
            target.setXmlDataFormat(xmlDataFormat);
        }
        if (!componentProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<>();
            for (RestPropertyDefinition prop : componentProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setComponentProperties(props);
        }
        if (!endpointProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<>();
            for (RestPropertyDefinition prop : endpointProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setEndpointProperties(props);
        }
        if (!consumerProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<>();
            for (RestPropertyDefinition prop : consumerProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setConsumerProperties(props);
        }
        if (!dataFormatProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<>();
            for (RestPropertyDefinition prop : dataFormatProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setDataFormatProperties(props);
        }
        if (!apiProperties.isEmpty()) {
            Map<String, Object> props = new HashMap<>();
            for (RestPropertyDefinition prop : apiProperties) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setApiProperties(props);
        }
        if (!corsHeaders.isEmpty()) {
            Map<String, String> props = new HashMap<>();
            for (RestPropertyDefinition prop : corsHeaders) {
                String key = prop.getKey();
                String value = CamelContextHelper.parseText(context, prop.getValue());
                props.put(key, value);
            }
            target.setCorsHeaders(props);
        }
        return target;
    }

}
