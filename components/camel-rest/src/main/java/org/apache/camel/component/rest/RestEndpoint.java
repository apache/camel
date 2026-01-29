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
package org.apache.camel.component.rest;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.MimeTypeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.RestProducerFactoryHelper.setupComponent;

/**
 * Expose REST services or call external REST services.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "rest", title = "REST", syntax = "rest:method:path:uriTemplate",
             category = { Category.CORE, Category.REST }, lenientProperties = true, headersClass = RestConstants.class)
@Metadata(annotations = {
        "protocol=http",
})
public class RestEndpoint extends DefaultEndpoint {

    public static final String[] DEFAULT_REST_CONSUMER_COMPONENTS
            = new String[] { "platform-http", "servlet", "jetty", "undertow", "netty-http", "coap" };
    public static final String[] DEFAULT_REST_PRODUCER_COMPONENTS
            = new String[] { "vertx-http", "http", "undertow", "netty-http" };
    public static final String DEFAULT_API_COMPONENT_NAME = "openapi";
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/rest/";

    private static final Logger LOG = LoggerFactory.getLogger(RestEndpoint.class);

    @UriPath(label = "common", enums = "get,post,put,delete,patch,head,trace,connect,options")
    @Metadata(required = true)
    private String method;
    @UriPath(label = "common")
    @Metadata(required = true)
    private String path;
    @UriPath(label = "common")
    private String uriTemplate;
    @UriParam(label = "common")
    private String consumes;
    @UriParam(label = "common")
    private String produces;
    @UriParam(label = "common")
    private String inType;
    @UriParam(label = "common")
    private String outType;
    @UriParam(label = "common")
    private String routeId;
    @UriParam(label = "consumer")
    private String description;
    @UriParam(label = "producer")
    private String apiDoc;
    @UriParam(label = "producer")
    private String host;
    @UriParam(label = "producer")
    private String queryParameters;
    @UriParam(label = "producer", enums = "auto,off,json,xml,json_xml")
    private RestConfiguration.RestBindingMode bindingMode;
    @UriParam(label = "producer")
    private String producerComponentName;
    @UriParam(label = "consumer")
    private String consumerComponentName;

    private Map<String, Object> parameters;

    public RestEndpoint(String endpointUri, RestComponent component) {
        super(endpointUri, component);
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        Object parameters = options.remove("parameters");
        if (parameters != null) {
            setParameters(PropertyConfigurerSupport.property(getCamelContext(), Map.class, parameters));
        }
        super.configureProperties(options);
    }

    @Override
    public RestComponent getComponent() {
        return (RestComponent) super.getComponent();
    }

    public String getMethod() {
        return method;
    }

    /**
     * HTTP method to use.
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    /**
     * The base path, can use &#42; as path suffix to support wildcard HTTP route matching.
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    /**
     * The uri template
     */
    public void setUriTemplate(String uriTemplate) {
        this.uriTemplate = uriTemplate;
    }

    public String getConsumes() {
        return consumes;
    }

    /**
     * Media type such as: 'text/xml', or 'application/json' this REST service accepts. By default we accept all kinds
     * of types.
     */
    public void setConsumes(String consumes) {
        this.consumes = MimeTypeHelper.sanitizeMimeType(consumes);
    }

    public String getProduces() {
        return produces;
    }

    /**
     * Media type such as: 'text/xml', or 'application/json' this REST service returns.
     */
    public void setProduces(String produces) {
        this.produces = MimeTypeHelper.sanitizeMimeType(produces);
    }

    public String getProducerComponentName() {
        return producerComponentName;
    }

    /**
     * The Camel Rest component to use for the producer REST transport, such as http, undertow. If no component has been
     * explicitly configured, then Camel will lookup if there is a Camel component that integrates with the Rest DSL, or
     * if a org.apache.camel.spi.RestProducerFactory is registered in the registry. If either one is found, then that is
     * being used.
     */
    public void setProducerComponentName(String producerComponentName) {
        this.producerComponentName = producerComponentName;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    /**
     * The Camel Rest component to use for the consumer REST transport, such as jetty, servlet, undertow. If no
     * component has been explicitly configured, then Camel will lookup if there is a Camel component that integrates
     * with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry. If either one
     * is found, then that is being used.
     */
    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public String getInType() {
        return inType;
    }

    /**
     * To declare the incoming POJO binding type as a FQN class name
     */
    public void setInType(String inType) {
        this.inType = inType;
    }

    public String getOutType() {
        return outType;
    }

    /**
     * To declare the outgoing POJO binding type as a FQN class name
     */
    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * Name of the route this REST services creates
     */
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Human description to document this REST service
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Additional parameters to configure the consumer of the REST transport for this REST service
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getApiDoc() {
        return apiDoc;
    }

    /**
     * The openapi api doc resource to use. The resource is loaded from classpath by default and must be in JSON format.
     */
    public void setApiDoc(String apiDoc) {
        this.apiDoc = apiDoc;
    }

    public String getHost() {
        return host;
    }

    /**
     * Host and port of HTTP service to use (override host in openapi schema)
     */
    public void setHost(String host) {
        this.host = host;
    }

    public String getQueryParameters() {
        return queryParameters;
    }

    /**
     * Query parameters for the HTTP service to call.
     *
     * The query parameters can contain multiple parameters separated by ampersand such such as foo=123&bar=456.
     */
    public void setQueryParameters(String queryParameters) {
        this.queryParameters = queryParameters;
    }

    public RestConfiguration.RestBindingMode getBindingMode() {
        return bindingMode;
    }

    /**
     * Configures the binding mode for the producer. If set to anything other than 'off' the producer will try to
     * convert the body of the incoming message from inType to the json or xml, and the response from json or xml to
     * outType.
     */
    public void setBindingMode(RestConfiguration.RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public void setBindingMode(String bindingMode) {
        this.bindingMode = RestConfiguration.RestBindingMode.valueOf(bindingMode.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Producer createProducer() throws Exception {
        validateHost();

        RestProducerFactory apiDocFactory = createApiDocFactory();
        ProducerFactoryResult factoryResult = findProducerFactory();

        if (factoryResult.factory == null) {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }

        LOG.debug("Using RestProducerFactory: {}", factoryResult.factory);

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), null, factoryResult.name);
        Producer producer = createProducerInstance(factoryResult.factory, apiDocFactory, config);

        RestProducer answer = new RestProducer(this, producer, config);
        answer.setOutType(outType);
        answer.setType(inType);
        answer.setBindingMode(bindingMode);

        return answer;
    }

    private void validateHost() {
        if (ObjectHelper.isEmpty(host)) {
            throw new IllegalArgumentException(
                    "Hostname must be configured on either restConfiguration"
                                               + " or in the rest endpoint uri as a query parameter with name host, eg rest:"
                                               + method + ":" + path + "?host=someserver");
        }
    }

    private RestProducerFactory createApiDocFactory() throws Exception {
        if (apiDoc == null) {
            return null;
        }
        LOG.debug("Discovering camel-openapi-java on classpath for using api-doc: {}", apiDoc);
        try {
            FactoryFinder finder = getCamelContext().getCamelContextExtension().getFactoryFinder(RESOURCE_PATH);
            RestProducerFactory apiDocFactory = finder.newInstance(DEFAULT_API_COMPONENT_NAME, RestProducerFactory.class)
                    .orElse(null);
            if (apiDocFactory == null) {
                throw new NoFactoryAvailableException("Cannot find camel-openapi-java on classpath");
            }
            parameters.put("apiDoc", apiDoc);
            return apiDocFactory;
        } catch (NoFactoryAvailableException e) {
            throw new IllegalStateException("Cannot find camel-openapi-java on classpath to use with api-doc: " + apiDoc);
        }
    }

    private record ProducerFactoryResult(RestProducerFactory factory, String name) {
    }

    @SuppressWarnings("unchecked")
    private ProducerFactoryResult findProducerFactory() {
        RestProducerFactory factory = null;
        String pname = getProducerComponentName();

        if (pname != null) {
            factory = lookupProducerFactory(pname);
        }

        if (factory == null) {
            ProducerFactoryResult result = findFromExistingComponents();
            if (result.factory != null) {
                return result;
            }
        }

        if (factory == null && pname == null && getConsumerComponentName() != null) {
            ProducerFactoryResult result = tryConsumerComponentAsProducer();
            if (result.factory != null) {
                return result;
            }
        }

        if (factory == null) {
            factory = findInRegistry();
        }

        if (factory == null) {
            ProducerFactoryResult result = findFromDefaultComponents();
            if (result.factory != null) {
                return result;
            }
        }

        return new ProducerFactoryResult(factory, pname);
    }

    @SuppressWarnings("unchecked")
    private RestProducerFactory lookupProducerFactory(String pname) {
        Object comp = getCamelContext().getRegistry().lookupByName(pname);
        if (comp instanceof RestProducerFactory restProducerFactory) {
            return restProducerFactory;
        }
        comp = setupComponent(pname, getCamelContext(), (Map<String, Object>) parameters.get("component"));
        if (comp instanceof RestProducerFactory restProducerFactory) {
            return restProducerFactory;
        }
        if (comp != null) {
            throw new IllegalArgumentException("Component " + pname + " is not a RestProducerFactory");
        }
        throw new NoSuchBeanException(pname, RestProducerFactory.class.getName());
    }

    @SuppressWarnings("unchecked")
    private ProducerFactoryResult findFromExistingComponents() {
        for (String name : getCamelContext().getComponentNames()) {
            Component comp = setupComponent(name, getCamelContext(), (Map<String, Object>) parameters.get("component"));
            if (comp instanceof RestProducerFactory producerFactory) {
                return new ProducerFactoryResult(producerFactory, name);
            }
        }
        return new ProducerFactoryResult(null, null);
    }

    @SuppressWarnings("unchecked")
    private ProducerFactoryResult tryConsumerComponentAsProducer() {
        String cname = getConsumerComponentName();
        Object comp = getCamelContext().getRegistry().lookupByName(cname);
        if (comp instanceof RestProducerFactory restProducerFactory) {
            return new ProducerFactoryResult(restProducerFactory, cname);
        }
        comp = setupComponent(cname, getCamelContext(), (Map<String, Object>) parameters.get("component"));
        if (comp instanceof RestProducerFactory restProducerFactory) {
            return new ProducerFactoryResult(restProducerFactory, cname);
        }
        return new ProducerFactoryResult(null, null);
    }

    private RestProducerFactory findInRegistry() {
        Set<RestProducerFactory> factories = getCamelContext().getRegistry().findByType(RestProducerFactory.class);
        if (factories != null && factories.size() == 1) {
            return factories.iterator().next();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ProducerFactoryResult findFromDefaultComponents() {
        for (String name : DEFAULT_REST_PRODUCER_COMPONENTS) {
            Object comp = setupComponent(name, getCamelContext(), (Map<String, Object>) parameters.get("component"));
            if (comp instanceof RestProducerFactory restProducerFactory) {
                LOG.debug("Auto discovered {} as RestProducerFactory", name);
                return new ProducerFactoryResult(restProducerFactory, name);
            }
        }
        return new ProducerFactoryResult(null, null);
    }

    private Producer createProducerInstance(
            RestProducerFactory factory, RestProducerFactory apiDocFactory, RestConfiguration config)
            throws Exception {
        if (apiDocFactory != null) {
            parameters.put("restProducerFactory", factory);
            return apiDocFactory.createProducer(getCamelContext(), host, method, path, uriTemplate, queryParameters,
                    consumes, produces, config, parameters);
        }
        return factory.createProducer(getCamelContext(), host, method, path, uriTemplate, queryParameters,
                consumes, produces, config, parameters);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ConsumerFactoryResult factoryResult = findConsumerFactory();

        if (factoryResult.factory == null) {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), factoryResult.name);
        String[] urls = buildServiceUrls(config);
        String baseUrl = urls[0];
        String url = urls[1];

        Consumer consumer = factoryResult.factory.createConsumer(getCamelContext(), processor, getMethod(), getPath(),
                getUriTemplate(), getConsumes(), getProduces(), config, getParameters());
        configureConsumer(consumer);

        // add to rest registry, so we can keep track of them
        getCamelContext().getRestRegistry().addRestService(consumer, false, url, baseUrl, getPath(), getUriTemplate(),
                getMethod(), getConsumes(), getProduces(), getInType(), getOutType(), getRouteId(), getDescription());
        return consumer;
    }

    private record ConsumerFactoryResult(RestConsumerFactory factory, String name) {
    }

    private ConsumerFactoryResult findConsumerFactory() {
        if (getConsumerComponentName() != null) {
            ConsumerFactoryResult result = lookupConsumerFactory(getConsumerComponentName());
            if (result.factory != null) {
                return result;
            }
        }

        ConsumerFactoryResult result = findConsumerFromExistingComponents();
        if (result.factory != null) {
            return result;
        }

        result = findPlatformHttpFactory();
        if (result.factory != null) {
            return result;
        }

        RestConsumerFactory factory = findConsumerInRegistry();
        if (factory != null) {
            return new ConsumerFactoryResult(factory, null);
        }

        return findConsumerFromDefaultComponents();
    }

    private ConsumerFactoryResult lookupConsumerFactory(String cname) {
        Object comp = getCamelContext().getRegistry().lookupByName(cname);
        if (comp instanceof RestConsumerFactory restConsumerFactory) {
            return new ConsumerFactoryResult(restConsumerFactory, cname);
        }
        comp = getCamelContext().getComponent(cname);
        if (comp instanceof RestConsumerFactory restConsumerFactory) {
            return new ConsumerFactoryResult(restConsumerFactory, cname);
        }
        if (comp != null) {
            throw new IllegalArgumentException("Component " + cname + " is not a RestConsumerFactory");
        }
        throw new NoSuchBeanException(cname, RestConsumerFactory.class.getName());
    }

    private ConsumerFactoryResult findConsumerFromExistingComponents() {
        for (String name : getCamelContext().getComponentNames()) {
            Component comp = getCamelContext().getComponent(name);
            if (comp instanceof RestConsumerFactory restConsumerFactory) {
                return new ConsumerFactoryResult(restConsumerFactory, name);
            }
        }
        return new ConsumerFactoryResult(null, null);
    }

    private ConsumerFactoryResult findPlatformHttpFactory() {
        Object comp = getCamelContext().getComponent("platform-http", true);
        if (comp instanceof RestConsumerFactory restConsumerFactory) {
            LOG.debug("Auto discovered platform-http as RestConsumerFactory");
            return new ConsumerFactoryResult(restConsumerFactory, "platform-http");
        }
        return new ConsumerFactoryResult(null, null);
    }

    private RestConsumerFactory findConsumerInRegistry() {
        Set<RestConsumerFactory> factories = getCamelContext().getRegistry().findByType(RestConsumerFactory.class);
        if (factories != null && factories.size() == 1) {
            return factories.iterator().next();
        }
        return null;
    }

    private ConsumerFactoryResult findConsumerFromDefaultComponents() {
        for (String name : DEFAULT_REST_CONSUMER_COMPONENTS) {
            Object comp = getCamelContext().getComponent(name, true);
            if (comp instanceof RestConsumerFactory restConsumerFactory) {
                LOG.debug("Auto discovered {} as RestConsumerFactory", name);
                return new ConsumerFactoryResult(restConsumerFactory, name);
            }
        }
        return new ConsumerFactoryResult(null, null);
    }

    private String[] buildServiceUrls(RestConfiguration config) throws Exception {
        String scheme = config.getScheme() != null ? config.getScheme() : "http";
        String host = config.getHost() != null ? config.getHost() : "";
        int port = config.getPort() > 0 ? config.getPort() : 80;

        host = resolveHostName(host, config);

        String path = buildPath(config);
        String baseUrl = scheme + "://" + host + (port != 80 ? ":" + port : "") + path;
        String url = buildFullUrl(baseUrl);

        return new String[] { baseUrl, url };
    }

    private String resolveHostName(String host, RestConfiguration config) throws Exception {
        if (ObjectHelper.isNotEmpty(host)) {
            return host;
        }
        if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
            return "0.0.0.0";
        }
        if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
            return HostUtils.getLocalHostName();
        }
        if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
            return HostUtils.getLocalIp();
        }
        return host;
    }

    private String buildPath(RestConfiguration config) {
        String path = getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String contextPath = config.getContextPath();
        if (contextPath != null) {
            if (!contextPath.startsWith("/")) {
                path = "/" + contextPath + path;
            } else {
                path = contextPath + path;
            }
        }
        return path;
    }

    private String buildFullUrl(String baseUrl) {
        if (uriTemplate == null) {
            return baseUrl;
        }
        if (uriTemplate.startsWith("/")) {
            return baseUrl + uriTemplate;
        }
        return baseUrl + "/" + uriTemplate;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
