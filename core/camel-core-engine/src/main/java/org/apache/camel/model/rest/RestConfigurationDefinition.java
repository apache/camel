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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;

/**
 * To configure rest
 */
@Metadata(label = "rest")
@XmlRootElement(name = "restConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestConfigurationDefinition {

    @XmlAttribute
    private String component;

    @XmlAttribute
    @Metadata(label = "consumer", defaultValue = "swagger")
    private String apiComponent;

    @XmlAttribute
    @Metadata(label = "producer")
    private String producerComponent;

    @XmlAttribute
    private String scheme;

    @XmlAttribute
    private String host;

    @XmlAttribute
    private String apiHost;

    @XmlAttribute
    @Metadata(defaultValue = "true", label = "consumer")
    private Boolean useXForwardHeaders;

    @XmlAttribute
    private String port;

    @XmlAttribute
    @Metadata(label = "producer")
    private String producerApiDoc;

    @XmlAttribute
    @Metadata(label = "consumer")
    private String contextPath;

    @XmlAttribute
    @Metadata(label = "consumer")
    private String apiContextPath;

    @XmlAttribute
    @Metadata(label = "consumer")
    private String apiContextRouteId;

    @XmlAttribute
    @Metadata(label = "consumer")
    private String apiContextIdPattern;

    @XmlAttribute
    @Metadata(label = "consumer")
    private Boolean apiContextListing;

    @XmlAttribute
    @Metadata(label = "consumer")
    private Boolean apiVendorExtension;

    @XmlAttribute
    @Metadata(label = "consumer")
    private RestHostNameResolver hostNameResolver;

    @XmlAttribute
    @Metadata(defaultValue = "off")
    private RestBindingMode bindingMode;

    @XmlAttribute
    private Boolean skipBindingOnErrorCode;

    @XmlAttribute
    private Boolean clientRequestValidation;

    @XmlAttribute
    @Metadata(label = "consumer")
    private Boolean enableCORS;

    @XmlAttribute
    private String jsonDataFormat;

    @XmlAttribute
    private String xmlDataFormat;

    @XmlElement(name = "componentProperty")
    private List<RestPropertyDefinition> componentProperties = new ArrayList<>();

    @XmlElement(name = "endpointProperty")
    private List<RestPropertyDefinition> endpointProperties = new ArrayList<>();

    @XmlElement(name = "consumerProperty")
    @Metadata(label = "consumer")
    private List<RestPropertyDefinition> consumerProperties = new ArrayList<>();

    @XmlElement(name = "dataFormatProperty")
    private List<RestPropertyDefinition> dataFormatProperties = new ArrayList<>();

    @XmlElement(name = "apiProperty")
    @Metadata(label = "consumer")
    private List<RestPropertyDefinition> apiProperties = new ArrayList<>();

    @XmlElement(name = "corsHeaders")
    @Metadata(label = "consumer")
    private List<RestPropertyDefinition> corsHeaders = new ArrayList<>();

    public String getComponent() {
        return component;
    }

    /**
     * The Camel Rest component to use for the REST transport (consumer), such
     * as netty-http, jetty, servlet, undertow. If no component has been explicit configured,
     * then Camel will lookup if there is a Camel component that integrates with
     * the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is
     * registered in the registry. If either one is found, then that is being
     * used.
     */
    public void setComponent(String component) {
        this.component = component;
    }

    public String getApiComponent() {
        return apiComponent;
    }

    /**
     * The name of the Camel component to use as the REST API (such as swagger)
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
     * The scheme to use for exposing the REST service. Usually http or https is
     * supported.
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
     * To use an specific hostname for the API documentation (eg swagger)
     * <p/>
     * This can be used to override the generated host with this configured
     * hostname
     */
    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getPort() {
        return port;
    }

    /**
     * The port number to use for exposing the REST service. Notice if you use
     * servlet component then the port number configured here does not apply, as
     * the port number in use is the actual port number the servlet component is
     * using. eg if using Apache Tomcat its the tomcat http port, if using
     * Apache Karaf its the HTTP service in Karaf that uses port 8181 by default
     * etc. Though in those situations setting the port number here, allows
     * tooling and JMX to know the port number, so its recommended to set the
     * port number to the number that the servlet engine uses.
     */
    public void setPort(String port) {
        this.port = port;
    }

    public String getProducerApiDoc() {
        return producerApiDoc;
    }

    /**
     * Sets the location of the api document (swagger api) the REST producer
     * will use to validate the REST uri and query parameters are valid
     * accordingly to the api document. This requires adding camel-swagger-java
     * to the classpath, and any miss configuration will let Camel fail on
     * startup and report the error(s).
     * <p/>
     * The location of the api document is loaded from classpath by default, but
     * you can use <tt>file:</tt> or <tt>http:</tt> to refer to resources to
     * load from file or http url.
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
     * This can be used when using components such as <tt>camel-servlet</tt>
     * where the deployed web application is deployed using a context-path. Or
     * for components such as <tt>camel-jetty</tt> or <tt>camel-netty-http</tt>
     * that includes a HTTP server.
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
     * This can be used when using components such as <tt>camel-servlet</tt>
     * where the deployed web application is deployed using a context-path.
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

    public String getApiContextIdPattern() {
        return apiContextIdPattern;
    }

    /**
     * Sets an CamelContext id pattern to only allow Rest APIs from rest
     * services within CamelContext's which name matches the pattern.
     * <p/>
     * The pattern <tt>#name#</tt> refers to the CamelContext name, to match on
     * the current CamelContext only. For any other value, the pattern uses the
     * rules from {@link PatternHelper#matchPattern(String, String)}
     *
     * @param apiContextIdPattern the pattern
     */
    public void setApiContextIdPattern(String apiContextIdPattern) {
        this.apiContextIdPattern = apiContextIdPattern;
    }

    public Boolean getApiContextListing() {
        return apiContextListing;
    }

    /**
     * Sets whether listing of all available CamelContext's with REST services
     * in the JVM is enabled. If enabled it allows to discover these contexts,
     * if <tt>false</tt> then only the current CamelContext is in use.
     */
    public void setApiContextListing(Boolean apiContextListing) {
        this.apiContextListing = apiContextListing;
    }

    public Boolean getApiVendorExtension() {
        return apiVendorExtension;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then
     * Camel will include additional information as vendor extension (eg keys
     * starting with x-) such as route ids, class names etc. Not all 3rd party
     * API gateways and tools supports vendor-extensions when importing your API
     * docs.
     */
    public void setApiVendorExtension(Boolean apiVendorExtension) {
        this.apiVendorExtension = apiVendorExtension;
    }

    public RestHostNameResolver getHostNameResolver() {
        return hostNameResolver;
    }

    /**
     * If no hostname has been explicit configured, then this resolver is used
     * to compute the hostname the REST service will be using.
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

    public Boolean getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding on output if there is a custom HTTP error code
     * header. This allows to build custom error messages that do not bind to
     * json / xml etc, as success messages otherwise will do.
     */
    public void setSkipBindingOnErrorCode(Boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public Boolean getClientRequestValidation() {
        return clientRequestValidation;
    }

    /**
     * Whether to enable validation of the client request to check whether the
     * Content-Type and Accept headers from the client is supported by the
     * Rest-DSL configuration of its consumes/produces settings.
     * <p/>
     * This can be turned on, to enable this check. In case of validation error,
     * then HTTP Status codes 415 or 406 is returned.
     * <p/>
     * The default value is false.
     */
    public void setClientRequestValidation(Boolean clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public Boolean getEnableCORS() {
        return enableCORS;
    }

    /**
     * Whether to enable CORS headers in the HTTP response.
     * <p/>
     * The default value is false.
     */
    public void setEnableCORS(Boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    /**
     * Name of specific json data format to use. By default json-jackson will be
     * used. Important: This option is only for setting a custom name of the
     * data format, not to refer to an existing data format instance.
     */
    public void setJsonDataFormat(String jsonDataFormat) {
        this.jsonDataFormat = jsonDataFormat;
    }

    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    /**
     * Name of specific XML data format to use. By default jaxb will be used.
     * Important: This option is only for setting a custom name of the data
     * format, not to refer to an existing data format instance.
     */
    public void setXmlDataFormat(String xmlDataFormat) {
        this.xmlDataFormat = xmlDataFormat;
    }

    public List<RestPropertyDefinition> getComponentProperties() {
        return componentProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest component
     * in use.
     */
    public void setComponentProperties(List<RestPropertyDefinition> componentProperties) {
        this.componentProperties = componentProperties;
    }

    public List<RestPropertyDefinition> getEndpointProperties() {
        return endpointProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest endpoint
     * in use.
     */
    public void setEndpointProperties(List<RestPropertyDefinition> endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public List<RestPropertyDefinition> getConsumerProperties() {
        return consumerProperties;
    }

    /**
     * Allows to configure as many additional properties for the rest consumer
     * in use.
     */
    public void setConsumerProperties(List<RestPropertyDefinition> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public List<RestPropertyDefinition> getDataFormatProperties() {
        return dataFormatProperties;
    }

    /**
     * Allows to configure as many additional properties for the data formats in
     * use. For example set property prettyPrint to true to have json outputted
     * in pretty mode. The properties can be prefixed to denote the option is
     * only for either JSON or XML and for either the IN or the OUT. The
     * prefixes are:
     * <ul>
     * <li>json.in.</li>
     * <li>json.out.</li>
     * <li>xml.in.</li>
     * <li>xml.out.</li>
     * </ul>
     * For example a key with value "xml.out.mustBeJAXBElement" is only for the
     * XML data format for the outgoing. A key without a prefix is a common key
     * for all situations.
     */
    public void setDataFormatProperties(List<RestPropertyDefinition> dataFormatProperties) {
        this.dataFormatProperties = dataFormatProperties;
    }

    public List<RestPropertyDefinition> getApiProperties() {
        return apiProperties;
    }

    /**
     * Allows to configure as many additional properties for the api
     * documentation (swagger). For example set property api.title to my cool
     * stuff
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

    public Boolean getUseXForwardHeaders() {
        return useXForwardHeaders;
    }

    /**
     * Whether to use X-Forward headers for Host and related setting.
     * <p/>
     * The default value is true.
     */
    public void setUseXForwardHeaders(Boolean useXForwardHeaders) {
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
     * To define a specific host to use for API documentation (eg swagger)
     * instead of using a generated API hostname that is relative to the REST
     * service host.
     */
    public RestConfigurationDefinition apiHost(String host) {
        setApiHost(host);
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
     * Sets the location of the api document (swagger api) the REST producer
     * will use to validate the REST uri and query parameters are valid
     * accordingly to the api document. This requires adding camel-swagger-java
     * to the classpath, and any miss configuration will let Camel fail on
     * startup and report the error(s).
     * <p/>
     * The location of the api document is loaded from classpath by default, but
     * you can use <tt>file:</tt> or <tt>http:</tt> to refer to resources to
     * load from file or http url.
     */
    public RestConfigurationDefinition producerApiDoc(String apiDoc) {
        setProducerApiDoc(apiDoc);
        return this;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt>
     * where the deployed web application is deployed using a context-path. Or
     * for components such as <tt>camel-jetty</tt> or <tt>camel-netty-http</tt>
     * that includes a HTTP server.
     */
    public RestConfigurationDefinition apiContextPath(String contextPath) {
        setApiContextPath(contextPath);
        return this;
    }

    /**
     * Sets the route id to use for the route that services the REST API.
     */
    public RestConfigurationDefinition apiContextRouteId(String routeId) {
        setApiContextRouteId(routeId);
        return this;
    }

    /**
     * Sets an CamelContext id pattern to only allow Rest APIs from rest
     * services within CamelContext's which name matches the pattern.
     * <p/>
     * The pattern uses the following rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the
     * pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     */
    public RestConfigurationDefinition apiContextIdPattern(String pattern) {
        setApiContextIdPattern(pattern);
        return this;
    }

    /**
     * Sets whether listing of all available CamelContext's with REST services
     * in the JVM is enabled. If enabled it allows to discover these contexts,
     * if <tt>false</tt> then only the current CamelContext is in use.
     */
    public RestConfigurationDefinition apiContextListing(boolean listing) {
        setApiContextListing(listing);
        return this;
    }

    /**
     * Whether vendor extension is enabled in the Rest APIs. If enabled then
     * Camel will include additional information as vendor extension (eg keys
     * starting with x-) such as route ids, class names etc. Some API tooling
     * may not support vendor extensions and this option can then be turned off.
     */
    public RestConfigurationDefinition apiVendorExtension(boolean vendorExtension) {
        setApiVendorExtension(vendorExtension);
        return this;
    }

    /**
     * Sets a leading context-path the REST services will be using.
     * <p/>
     * This can be used when using components such as <tt>camel-servlet</tt>
     * where the deployed web application is deployed using a context-path.
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
     * To specify whether to skip binding output if there is a custom HTTP error
     * code
     */
    public RestConfigurationDefinition skipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        return this;
    }

    /**
     * Whether to enable validation of the client request to check whether the
     * Content-Type and Accept headers from the client is supported by the
     * Rest-DSL configuration of its consumes/produces settings.
     */
    public RestConfigurationDefinition clientRequestValidation(boolean clientRequestValidation) {
        setClientRequestValidation(clientRequestValidation);
        return this;
    }

    /**
     * To specify whether to enable CORS which means Camel will automatic
     * include CORS in the HTTP headers in the response.
     */
    public RestConfigurationDefinition enableCORS(boolean enableCORS) {
        setEnableCORS(enableCORS);
        return this;
    }

    /**
     * To use a specific json data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the
     * data format, not to refer to an existing data format instance.
     *
     * @param name name of the data format to
     *            {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String)
     *            resolve}
     */
    public RestConfigurationDefinition jsonDataFormat(String name) {
        setJsonDataFormat(name);
        return this;
    }

    /**
     * To use a specific XML data format
     * <p/>
     * <b>Important:</b> This option is only for setting a custom name of the
     * data format, not to refer to an existing data format instance.
     *
     * @param name name of the data format to
     *            {@link org.apache.camel.CamelContext#resolveDataFormat(java.lang.String)
     *            resolve}
     */
    public RestConfigurationDefinition xmlDataFormat(String name) {
        setXmlDataFormat(name);
        return this;
    }

    /**
     * For additional configuration options on component level
     * <p/>
     * The value can use <tt>#</tt> to refer to a bean to lookup in the
     * registry.
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
     * The value can use <tt>#</tt> to refer to a bean to lookup in the
     * registry.
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
     * The value can use <tt>#</tt> to refer to a bean to lookup in the
     * registry.
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
     * The value can use <tt>#</tt> to refer to a bean to lookup in the
     * registry.
     */
    public RestConfigurationDefinition dataFormatProperty(String key, String value) {
        RestPropertyDefinition prop = new RestPropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        getDataFormatProperties().add(prop);
        return this;
    }

    /**
     * For configuring an api property, such as <tt>api.title</tt>, or
     * <tt>api.version</tt>.
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
        setUseXForwardHeaders(useXForwardHeaders);
        return this;
    }

    // Implementation
    // -------------------------------------------------------------------------

    /**
     * Configured an instance of a {@link org.apache.camel.spi.RestConfiguration} instance based
     * on the definition
     *
     * @param context the camel context
     * @param target the {@link org.apache.camel.spi.RestConfiguration} target
     * @return the configuration
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
            target.setUseXForwardHeaders(useXForwardHeaders);
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
        if (apiContextIdPattern != null) {
            // special to allow #name# to refer to itself
            if ("#name#".equals(apiComponent)) {
                target.setApiContextIdPattern(context.getName());
            } else {
                target.setApiContextIdPattern(CamelContextHelper.parseText(context, apiContextIdPattern));
            }
        }
        if (apiContextListing != null) {
            target.setApiContextListing(apiContextListing);
        }
        if (apiVendorExtension != null) {
            target.setApiVendorExtension(apiVendorExtension);
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
            target.setSkipBindingOnErrorCode(skipBindingOnErrorCode);
        }
        if (clientRequestValidation != null) {
            target.setClientRequestValidation(clientRequestValidation);
        }
        if (enableCORS != null) {
            target.setEnableCORS(enableCORS);
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
