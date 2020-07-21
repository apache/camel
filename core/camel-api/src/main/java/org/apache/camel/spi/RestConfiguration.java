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
package org.apache.camel.spi;

import java.util.Map;

/**
 * Configuration use by {@link org.apache.camel.spi.RestConsumerFactory} and {@link org.apache.camel.spi.RestApiConsumerFactory}
 * for Camel components to support the Camel {@link org.apache.camel.model.rest.RestDefinition rest} DSL.
 */
public class RestConfiguration {

    public static final String CORS_ACCESS_CONTROL_ALLOW_ORIGIN = "*";
    public static final String CORS_ACCESS_CONTROL_ALLOW_METHODS = "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH";
    public static final String CORS_ACCESS_CONTROL_MAX_AGE = "3600";
    public static final String CORS_ACCESS_CONTROL_ALLOW_HEADERS = "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers";

    public static final String DEFAULT_REST_CONFIGURATION_ID = "rest-configuration";

    public enum RestBindingMode {
        auto, off, json, xml, json_xml
    }

    public enum RestHostNameResolver {
        allLocalIp, localIp, localHostName
    }

    private String component;
    private String apiComponent;
    private String producerComponent;
    private String producerApiDoc;
    private String scheme;
    private String host;
    private boolean useXForwardHeaders = true;
    private String apiHost;
    private int port;
    private String contextPath;
    private String apiContextPath;
    private String apiContextRouteId;
    private String apiContextIdPattern;
    private boolean apiContextListing;
    private boolean apiVendorExtension;
    private RestHostNameResolver hostNameResolver = RestHostNameResolver.allLocalIp;
    private RestBindingMode bindingMode = RestBindingMode.off;
    private boolean skipBindingOnErrorCode = true;
    private boolean clientRequestValidation;
    private boolean enableCORS;
    private String jsonDataFormat;
    private String xmlDataFormat;
    private Map<String, Object> componentProperties;
    private Map<String, Object> endpointProperties;
    private Map<String, Object> consumerProperties;
    private Map<String, Object> dataFormatProperties;
    private Map<String, Object> apiProperties;
    private Map<String, String> corsHeaders;

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
     * @param componentName the name of the component (such as netty-http, jetty, servlet, undertow, etc.)
     */
    public void setComponent(String componentName) {
        this.component = componentName;
    }

    /**
     * Gets the name of the Camel component to use as the REST API (such as swagger)
     *
     * @return the component name, or <tt>null</tt> to let Camel use the default name <tt>swagger</tt>
     */
    public String getApiComponent() {
        return apiComponent;
    }

    /**
     * Sets the name of the Camel component to use as the REST API (such as swagger)
     *
     * @param apiComponent the name of the component (such as swagger)
     */
    public void setApiComponent(String apiComponent) {
        this.apiComponent = apiComponent;
    }

    /**
     * Gets the name of the Camel component to use as the REST producer
     *
     * @return the component name, or <tt>null</tt> to let Camel search the {@link Registry} to find suitable implementation
     */
    public String getProducerComponent() {
        return producerComponent;
    }

    /**
     * Sets the name of the Camel component to use as the REST producer
     *
     * @param componentName the name of the component (such as http, netty-http, undertow, etc.)
     */
    public void setProducerComponent(String componentName) {
        this.producerComponent = componentName;
    }

    /**
     * Gets the location of the api document (swagger api) the REST producer will use
     * to validate the REST uri and query parameters are valid accordingly to the api document.
     */
    public String getProducerApiDoc() {
        return producerApiDoc;
    }

    /**
     * Sets the location of the api document (swagger api) the REST producer will use
     * to validate the REST uri and query parameters are valid accordingly to the api document.
     * This requires adding camel-swagger-java to the classpath, and any miss configuration
     * will let Camel fail on startup and report the error(s).
     * <p/>
     * The location of the api document is loaded from classpath by default, but you can use
     * <tt>file:</tt> or <tt>http:</tt> to refer to resources to load from file or http url.
     */
    public void setProducerApiDoc(String producerApiDoc) {
        this.producerApiDoc = producerApiDoc;
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
     * WWhether to use X-Forward headers to set host etc. for Swagger.
     * <p/>
     * This option is default <tt>true</tt>.
     */
    public boolean isUseXForwardHeaders() {
        return useXForwardHeaders;
    }

    /**
     * WWhether to use X-Forward headers to set host etc. for Swagger.
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @param useXForwardHeaders whether to use X-Forward headers
     */
    public void setUseXForwardHeaders(boolean useXForwardHeaders) {
        this.useXForwardHeaders = useXForwardHeaders;
    }

    public String getApiHost() {
        return apiHost;
    }

    /**
     * To use an specific hostname for the API documentation (eg swagger)
     * <p/>
     * This can be used to override the generated host with this configured hostname
     */
    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
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
     * Gets the configured context-path
     *
     * @return the context path, or <tt>null</tt> if none configured.
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application
     * is deployed using a context-path. Or for components such as <tt>camel-jetty</tt> or <tt>camel-netty-http</tt>
     * that includes a HTTP server.
     *
     * @param contextPath the context path
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
     * This can be used when using components such as <tt>camel-servlet</tt> where the deployed web application
     * is deployed using a context-path.
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
     * @param apiContextRouteId  the route id
     */
    public void setApiContextRouteId(String apiContextRouteId) {
        this.apiContextRouteId = apiContextRouteId;
    }

    public String getApiContextIdPattern() {
        return apiContextIdPattern;
    }

    /**
     * Optional CamelContext id pattern to only allow Rest APIs from rest services within CamelContext's which name matches the pattern.
     * <p/>
     * The pattern <tt>#name#</tt> refers to the CamelContext name, to match on the current CamelContext only.
     * For any other value, the pattern uses the rules from {@link org.apache.camel.support.EndpointHelper#matchPattern(String, String)}
     *
     * @param apiContextIdPattern  the pattern
     */
    public void setApiContextIdPattern(String apiContextIdPattern) {
        this.apiContextIdPattern = apiContextIdPattern;
    }

    public boolean isApiContextListing() {
        return apiContextListing;
    }

    /**
     * Sets whether listing of all available CamelContext's with REST services in the JVM is enabled. If enabled it allows to discover
     * these contexts, if <tt>false</tt> then only the current CamelContext is in use.
     */
    public void setApiContextListing(boolean apiContextListing) {
        this.apiContextListing = apiContextListing;
    }

    public boolean isApiVendorExtension() {
        return apiVendorExtension;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then Camel will include additional information
     * as vendor extension (eg keys starting with x-) such as route ids, class names etc.
     * Not all 3rd party API gateways and tools supports vendor-extensions when importing your API docs.
     */
    public void setApiVendorExtension(boolean apiVendorExtension) {
        this.apiVendorExtension = apiVendorExtension;
    }

    /**
     * Gets the resolver to use for resolving hostname
     *
     * @return the resolver
     */
    public RestHostNameResolver getHostNameResolver() {
        return hostNameResolver;
    }

    /**
     * Sets the resolver to use for resolving hostname
     *
     * @param hostNameResolver the resolver
     */
    public void setHostNameResolver(RestHostNameResolver hostNameResolver) {
        this.hostNameResolver = hostNameResolver;
    }

    /**
     * Sets the resolver to use for resolving hostname
     *
     * @param hostNameResolver the resolver
     */
    public void setHostNameResolver(String hostNameResolver) {
        this.hostNameResolver = RestHostNameResolver.valueOf(hostNameResolver);
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
     * Whether to skip binding output if there is a custom HTTP error code, and instead use the response body as-is.
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @return whether to skip binding on error code
     */
    public boolean isSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding output if there is a custom HTTP error code, and instead use the response body as-is.
     * <p/>
     * This option is default <tt>true</tt>.
     *
     * @param skipBindingOnErrorCode whether to skip binding on error code
     */
    public void setSkipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public boolean isClientRequestValidation() {
        return clientRequestValidation;
    }

    /**
     * Whether to enable validation of the client request to check whether the Content-Type and Accept headers from
     * the client is supported by the Rest-DSL configuration of its consumes/produces settings.
     * <p/>
     * This can be turned on, to enable this check. In case of validation error, then HTTP Status codes 415 or 406 is returned.
     * <p/>
     * The default value is false.
     */
    public void setClientRequestValidation(boolean clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic include CORS in the HTTP headers in the response.
     * <p/>
     * This option is default <tt>false</tt>
     *
     * @return whether CORS is enabled or not
     */
    public boolean isEnableCORS() {
        return enableCORS;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic include CORS in the HTTP headers in the response.
     * <p/>
     * This option is default <tt>false</tt>
     *
     * @param enableCORS <tt>true</tt> to enable CORS
     */
    public void setEnableCORS(boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    /**
     * Gets the name of the json data format.
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
     *
     * @return the name, or <tt>null</tt> to use default
     */
    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    /**
     * Sets a custom json data format to be used
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
     *
     * @param name name of the data format
     */
    public void setJsonDataFormat(String name) {
        this.jsonDataFormat = name;
    }

    /**
     * Gets the name of the xml data format.
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
     *
     * @return the name, or <tt>null</tt> to use default
     */
    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    /**
     * Sets a custom xml data format to be used.
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the data format, not to refer to an existing data format instance.
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

    public Map<String, Object> getApiProperties() {
        return apiProperties;
    }

    /**
     * Sets additional options on api level
     *
     * @param apiProperties the options
     */
    public void setApiProperties(Map<String, Object> apiProperties) {
        this.apiProperties = apiProperties;
    }

    /**
     * Gets the CORS headers to use if CORS has been enabled.
     *
     * @return the CORS headers
     */
    public Map<String, String> getCorsHeaders() {
        return corsHeaders;
    }

    /**
     * Sets the CORS headers to use if CORS has been enabled.
     *
     * @param corsHeaders the CORS headers
     */
    public void setCorsHeaders(Map<String, String> corsHeaders) {
        this.corsHeaders = corsHeaders;
    }
}
