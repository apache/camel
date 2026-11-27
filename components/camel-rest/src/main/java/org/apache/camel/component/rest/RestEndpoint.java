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
import java.util.StringJoiner;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.ArtifactUtils;
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
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.BYTES, Carrier.NULL, Carrier.MAP, Carrier.FILE },
                                   dependsOn = "consumerComponentName",
                                   byOption = @BodyType.When(option = "bindingMode", value = "json",
                                                             carriers = { Carrier.MAP, Carrier.LIST, Carrier.POJO }),
                                   description = "The request body as the HTTP consumer gives it (platform-http by"
                                                 + " default). With bindingMode json, the object of inType, or a Map or a"
                                                 + " List without inType"),
           consumerReply = @BodyType(value = { Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM },
                                     dependsOn = "consumerComponentName",
                                     byOption = @BodyType.When(option = "bindingMode", value = "json", carriers = Carrier.ANY),
                                     description = "The response body as the HTTP consumer writes it. With bindingMode json,"
                                                   + " any object, which the binding writes as JSON"),
           producerIn = @BodyType(value = { Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  dependsOn = "producerComponentName",
                                  byOption = @BodyType.When(option = "bindingMode", value = "json", carriers = Carrier.ANY),
                                  description = "The request body the HTTP producer sends (the http component by default)."
                                                + " With bindingMode json, any object, which the binding writes as JSON"),
           producerOut = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.NULL },
                                   dependsOn = { "producerComponentName", "outType" },
                                   description = "The response body as the HTTP producer gives it; with bindingMode json and"
                                                 + " outType, an object of outType"))
public class RestEndpoint extends DefaultEndpoint {

    public static final String[] DEFAULT_REST_CONSUMER_COMPONENTS
            = new String[] { "platform-http", "servlet", "jetty", "undertow", "netty-http", "coap" };
    // camel-http first: the reference HTTP client, and the one most runtimes carry (CAMEL-24849)
    public static final String[] DEFAULT_REST_PRODUCER_COMPONENTS
            = new String[] { "http", "vertx-http", "undertow", "netty-http" };
    public static final String DEFAULT_API_COMPONENT_NAME = "openapi";
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/rest/";

    private static final Logger LOG = LoggerFactory.getLogger(RestEndpoint.class);

    @UriPath(label = "common", enums = "get,post,put,delete,patch,head,trace,connect,options",
             description = "The HTTP method of the REST service. The consumer answers requests with this method, and the"
                           + " producer sends the request with it.")
    @Metadata(required = true)
    private String method;
    @UriPath(label = "common",
             description = "The base path of the REST service, such as users. Without uriTemplate, it is the whole path,"
                           + " and it can hold {name} path parameters as in uriTemplate. A consumer can end it with * to"
                           + " match all the paths that start with it.")
    @Metadata(required = true)
    private String path;
    @UriPath(label = "common",
             description = "The path below the base path, such as {id}, which can hold {name} path parameters. The"
                           + " consumer sets each path parameter of a request as a header. The producer fills each {name}"
                           + " with the header of that name, or else the exchange variable of that name.")
    private String uriTemplate;
    @UriParam(label = "common",
              description = "The media types, separated by comma, that this endpoint takes in, such as application/json."
                            + " For the consumer, the media types of the request body that the REST service accepts. The"
                            + " producer sets it as the Accept header of the request when the message has no Accept"
                            + " header.")
    private String consumes;
    @UriParam(label = "common",
              description = "The media types, separated by comma, that this endpoint sends, such as application/json. For"
                            + " the consumer, the media types of the response body that the REST service returns. The"
                            + " producer sets it as the Content-Type header of the request when the message has no"
                            + " Content-Type header.")
    private String produces;
    @UriParam(label = "common",
              description = "The class name of the request body, with [] appended for an array. The consumer stores it in"
                            + " the rest registry. With xml binding, the producer uses it as the JAXB context of the"
                            + " request body.")
    private String inType;
    @UriParam(label = "common",
              description = "The class name of the response body, with [] appended for an array. With a binding mode other"
                            + " than off, the producer reads a JSON or XML response into this class, and without it the"
                            + " producer does not read the response. The consumer stores it in the rest registry.")
    private String outType;
    @UriParam(label = "common",
              description = "The id of the route of this REST service, which the consumer stores in the rest registry to"
                            + " count the requests of the service. The Rest DSL sets it to the id of the route that it"
                            + " creates. This option does not set the id of the route.")
    private String routeId;
    @UriParam(label = "consumer",
              description = "A description of this REST service, which the consumer stores in the rest registry. The Rest"
                            + " DSL sets it from the description of the route, the verb or the rest.")
    private String description;
    @UriParam(label = "producer",
              description = "The location of the OpenAPI document (JSON or YAML) of the REST service to call: a file, an"
                            + " http or https URL, or a classpath resource. When set, the producer needs camel-openapi-java,"
                            + " and fails to start if the document has no operation for the method and path. If not set, the"
                            + " apiDoc option of the component, or else the producerApiDoc of the rest configuration, is"
                            + " used.")
    private String apiDoc;
    @UriParam(label = "producer",
              description = "The scheme, host and port of the REST service to call, with an optional base path, such as"
                            + " http://myserver:8080/foo. The scheme is http when it is not given. If not set, the host"
                            + " option of the component, or else the host and port of the rest configuration, is used. The"
                            + " producer fails to start without a host.")
    private String host;
    @UriParam(label = "producer",
              description = "The query string of the request, such as foo=123&bar=456. A value {name} is filled with the"
                            + " header or the exchange variable of that name, and a parameter with the value {name?} is"
                            + " left out when neither is set. The endpoint also adds the parameters of its URI that it"
                            + " does not know, except the options of the component that producerComponentName names.")
    private String queryParameters;
    @UriParam(label = "producer", enums = "auto,off,json,xml,json_xml",
              description = "The binding mode of the producer, which replaces the bindingMode of the rest configuration"
                            + " (off by default). With json, xml or json_xml, the producer writes a POJO body as JSON or"
                            + " XML, and reads the response into outType. With auto, it chooses from the Content-Type header,"
                            + " and sends or keeps a body that it cannot bind as it is.")
    private RestConfiguration.RestBindingMode bindingMode;
    @UriParam(label = "producer",
              description = "The name of the Camel component that sends the HTTP request, such as http or vertx-http. If"
                            + " not set, the producerComponent of the rest configuration is used. If that is not set either,"
                            + " the producer takes the first component of the CamelContext that is a RestProducerFactory,"
                            + " then the component of consumerComponentName, then a single RestProducerFactory in the"
                            + " registry, then http, vertx-http, undertow or netty-http, whichever is on the classpath.")
    private String producerComponentName;
    @UriParam(label = "consumer",
              description = "The name of the Camel component that runs the REST service, such as platform-http, servlet or"
                            + " jetty. If not set, the component of the rest configuration is used. If that is not set"
                            + " either, the consumer takes the first component of the CamelContext that is a"
                            + " RestConsumerFactory, then platform-http, then a single RestConsumerFactory in the registry,"
                            + " then servlet, jetty, undertow, netty-http or coap, whichever is on the classpath.")
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

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public void setUriTemplate(String uriTemplate) {
        this.uriTemplate = uriTemplate;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = MimeTypeHelper.sanitizeMimeType(consumes);
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = MimeTypeHelper.sanitizeMimeType(produces);
    }

    public String getProducerComponentName() {
        return producerComponentName;
    }

    public void setProducerComponentName(String producerComponentName) {
        this.producerComponentName = producerComponentName;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public String getInType() {
        return inType;
    }

    public void setInType(String inType) {
        this.inType = inType;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getDescription() {
        return description;
    }

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

    public void setApiDoc(String apiDoc) {
        this.apiDoc = apiDoc;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(String queryParameters) {
        this.queryParameters = queryParameters;
    }

    public RestConfiguration.RestBindingMode getBindingMode() {
        return bindingMode;
    }

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
            throw new IllegalStateException(
                    factoryNotFound("RestProducerFactory", DEFAULT_REST_PRODUCER_COMPONENTS, "producerComponent"));
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
        RestProducerFactory apiDocFactory = ResolverHelper.resolveMandatoryService(getCamelContext(),
                "rest/" + DEFAULT_API_COMPONENT_NAME, RestProducerFactory.class, "camel-openapi-java");
        parameters.put("apiDoc", apiDoc);
        return apiDocFactory;
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
        throw new NoSuchBeanException(
                pname, RestProducerFactory.class.getName(),
                componentNameHint(pname, DEFAULT_REST_PRODUCER_COMPONENTS, "producerComponent"));
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
            throw new IllegalStateException(
                    factoryNotFound("RestConsumerFactory", DEFAULT_REST_CONSUMER_COMPONENTS, "component"));
        }

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), factoryResult.name);
        String[] urls = buildServiceUrls(config);
        String baseUrl = urls[0];
        String url = urls[1];

        Consumer consumer = factoryResult.factory.createConsumer(getCamelContext(), processor, getMethod(), getPath(),
                getUriTemplate(), getConsumes(), getProduces(), config, getParameters());
        configureConsumer(consumer);

        // add to rest registry, so we can keep track of them
        RestRegistry registry = PluginHelper.getRestRegistry(getCamelContext());
        registry.addRestService(consumer, false, url, baseUrl, getPath(), getUriTemplate(),
                getMethod(), getConsumes(), getProduces(), getInType(), getOutType(), getRouteId(), null, null,
                getDescription());
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
        throw new NoSuchBeanException(
                cname, RestConsumerFactory.class.getName(),
                componentNameHint(cname, DEFAULT_REST_CONSUMER_COMPONENTS, "component"));
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

    /**
     * The message when no factory is found: the components the Rest DSL looks for, each with the artifact that has it,
     * and the option that chooses one.
     */
    static String factoryNotFound(String factory, String[] components, String option) {
        StringJoiner names = new StringJoiner(", ");
        for (String name : components) {
            String artifact = ArtifactUtils.componentArtifact(name);
            names.add(artifact != null ? name + " (" + artifact + ")" : name);
        }
        return "Cannot find " + factory + " in Registry or as a Component to use. Add one of these components to the"
               + " classpath: " + names + ". To choose one, set the " + option
               + " option of the rest configuration to its name.";
    }

    /**
     * The hint when the component the option names is not found: the artifact to add when it is a Camel component, or
     * else the names the option takes (it is the name of a component, not of a bean or a servlet).
     */
    static String componentNameHint(String name, String[] components, String option) {
        String hint = ArtifactUtils.componentHint(name);
        if (ArtifactUtils.componentArtifact(name) != null) {
            return hint;
        }
        return hint + ". The " + option + " option of the rest configuration takes the name of a component that"
               + " supports the Rest DSL: " + String.join(", ", components);
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
