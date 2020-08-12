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
import org.apache.camel.ExtendedCamelContext;
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
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.RestProducerFactoryHelper.setupComponent;

/**
 * Expose REST services or call external REST services.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "rest", title = "REST", syntax = "rest:method:path:uriTemplate", category = {Category.CORE, Category.REST}, lenientProperties = true)
public class RestEndpoint extends DefaultEndpoint {

    public static final String[] DEFAULT_REST_CONSUMER_COMPONENTS = new String[]{"coap", "netty-http", "jetty", "servlet", "spark-java", "undertow"};
    public static final String[] DEFAULT_REST_PRODUCER_COMPONENTS = new String[]{"http", "netty-http", "undertow"};
    public static final String DEFAULT_API_COMPONENT_NAME = "openapi";
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/rest/";

    private static final Logger LOG = LoggerFactory.getLogger(RestEndpoint.class);

    @UriPath(label = "common", enums = "get,post,put,delete,patch,head,trace,connect,options") @Metadata(required = true)
    private String method;
    @UriPath(label = "common") @Metadata(required = true)
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
     * The base path
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
     * Media type such as: 'text/xml', or 'application/json' this REST service accepts.
     * By default we accept all kinds of types.
     */
    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    /**
     * Media type such as: 'text/xml', or 'application/json' this REST service returns.
     */
    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getProducerComponentName() {
        return producerComponentName;
    }

    /**
     * The Camel Rest component to use for (producer) the REST transport, such as http, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestProducerFactory is registered in the registry.
     * If either one is found, then that is being used.
     */
    public void setProducerComponentName(String producerComponentName) {
        this.producerComponentName = producerComponentName;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    /**
     * The Camel Rest component to use for (consumer) the REST transport, such as jetty, servlet, undertow.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry.
     * If either one is found, then that is being used.
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
     * The openapi api doc resource to use.
     * The resource is loaded from classpath by default and must be in JSON format.
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
     * Configures the binding mode for the producer. If set to anything
     * other than 'off' the producer will try to convert the body of
     * the incoming message from inType to the json or xml, and the
     * response from json or xml to outType.
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
        if (ObjectHelper.isEmpty(host)) {
            // hostname must be provided
            throw new IllegalArgumentException("Hostname must be configured on either restConfiguration"
                + " or in the rest endpoint uri as a query parameter with name host, eg rest:" + method + ":" + path + "?host=someserver");
        }

        RestProducerFactory apiDocFactory = null;
        RestProducerFactory factory = null;

        if (apiDoc != null) {
            LOG.debug("Discovering camel-openapi-java on classpath for using api-doc: {}", apiDoc);
            // lookup on classpath using factory finder to automatic find it (just add camel-openapi-java to classpath etc)
            FactoryFinder finder = null;
            try {
                finder = getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
                apiDocFactory = finder.newInstance(DEFAULT_API_COMPONENT_NAME, RestProducerFactory.class).orElse(null);
                if (apiDocFactory == null) {
                    throw new NoFactoryAvailableException("Cannot find camel-openapi-java on classpath");
                }
                parameters.put("apiDoc", apiDoc);
            } catch (NoFactoryAvailableException e) {
                try {
                    LOG.debug("Discovering camel-swagger-java on classpath as fallback for using api-doc: {}", apiDoc);
                    Object instance = finder.newInstance("swagger").get();
                    if (instance instanceof RestProducerFactory) {
                        // this factory from camel-swagger-java will facade the http component in use
                        apiDocFactory = (RestProducerFactory) instance;
                    }
                    parameters.put("apiDoc", apiDoc);
                } catch (Exception ex) {

                    throw new IllegalStateException("Cannot find camel-openapi-java neither camel-swagger-java on classpath to use with api-doc: " + apiDoc);
                }

            }
        }

        String pname = getProducerComponentName();
        if (pname != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(pname);
            if (comp instanceof RestProducerFactory) {
                factory = (RestProducerFactory) comp;
            } else {
                comp = setupComponent(getProducerComponentName(), getCamelContext(), (Map<String, Object>) parameters.get("component"));
                if (comp instanceof RestProducerFactory) {
                    factory = (RestProducerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + pname + " is not a RestProducerFactory");
                } else {
                    throw new NoSuchBeanException(getProducerComponentName(), RestProducerFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = setupComponent(name, getCamelContext(), (Map<String, Object>) parameters.get("component"));
                if (comp instanceof RestProducerFactory) {
                    factory = (RestProducerFactory) comp;
                    pname = name;
                    break;
                }
            }
        }

        // fallback to use consumer name as it may be producer capable too
        if (pname == null && getConsumerComponentName() != null) {
            String cname = getConsumerComponentName();
            Object comp = getCamelContext().getRegistry().lookupByName(cname);
            if (comp instanceof RestProducerFactory) {
                factory = (RestProducerFactory) comp;
                pname = cname;
            } else {
                comp = setupComponent(cname, getCamelContext(), (Map<String, Object>) parameters.get("component"));
                if (comp instanceof RestProducerFactory) {
                    factory = (RestProducerFactory) comp;
                    pname = cname;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestProducerFactory> factories = getCamelContext().getRegistry().findByType(RestProducerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        // no explicit factory found then try to see if we can find any of the default rest producer components
        // and there must only be exactly one so we safely can pick this one
        if (factory == null) {
            RestProducerFactory found = null;
            String foundName = null;
            for (String name : DEFAULT_REST_PRODUCER_COMPONENTS) {
                Object comp = setupComponent(name, getCamelContext(), (Map<String, Object>) parameters.get("component"));
                if (comp instanceof RestProducerFactory) {
                    if (found == null) {
                        found = (RestProducerFactory) comp;
                        foundName = name;
                    } else {
                        throw new IllegalArgumentException("Multiple RestProducerFactory found on classpath. Configure explicit which component to use");
                    }
                }
            }
            if (found != null) {
                LOG.debug("Auto discovered {} as RestProducerFactory", foundName);
                factory = found;
            }
        }

        if (factory != null) {
            LOG.debug("Using RestProducerFactory: {}", factory);

            // here we look for the producer part so we should not care about the component
            // configured for the consumer part
            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), null, pname);

            Producer producer;
            if (apiDocFactory != null) {
                // wrap the factory using the api doc factory which will use the factory
                parameters.put("restProducerFactory", factory);
                producer = apiDocFactory.createProducer(getCamelContext(), host, method, path, uriTemplate, queryParameters, consumes, produces, config, parameters);
            } else {
                producer = factory.createProducer(getCamelContext(), host, method, path, uriTemplate, queryParameters, consumes, produces, config, parameters);
            }

            RestProducer answer = new RestProducer(this, producer, config);
            answer.setOutType(outType);
            answer.setType(inType);
            answer.setBindingMode(bindingMode);

            return answer;
        } else {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestConsumerFactory factory = null;
        String cname = null;
        if (getConsumerComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getConsumerComponentName());
            if (comp instanceof RestConsumerFactory) {
                factory = (RestConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getConsumerComponentName());
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + getConsumerComponentName() + " is not a RestConsumerFactory");
                } else {
                    throw new NoSuchBeanException(getConsumerComponentName(), RestConsumerFactory.class.getName());
                }
            }
            cname = getConsumerComponentName();
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                    cname = name;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestConsumerFactory> factories = getCamelContext().getRegistry().findByType(RestConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        // no explicit factory found then try to see if we can find any of the default rest consumer components
        // and there must only be exactly one so we safely can pick this one
        if (factory == null) {
            RestConsumerFactory found = null;
            String foundName = null;
            for (String name : DEFAULT_REST_CONSUMER_COMPONENTS) {
                Object comp = getCamelContext().getComponent(name, true);
                if (comp instanceof RestConsumerFactory) {
                    if (found == null) {
                        found = (RestConsumerFactory) comp;
                        foundName = name;
                    } else {
                        throw new IllegalArgumentException("Multiple RestConsumerFactory found on classpath. Configure explicit which component to use");
                    }
                }
            }
            if (found != null) {
                LOG.debug("Auto discovered {} as RestConsumerFactory", foundName);
                factory = found;
            }
        }

        if (factory != null) {
            // if no explicit port/host configured, then use port from rest configuration
            String scheme = "http";
            String host = "";
            int port = 80;

            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), cname);
            if (config.getScheme() != null) {
                scheme = config.getScheme();
            }
            if (config.getHost() != null) {
                host = config.getHost();
            }
            int num = config.getPort();
            if (num > 0) {
                port = num;
            }

            // if no explicit hostname set then resolve the hostname
            if (ObjectHelper.isEmpty(host)) {
                if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                    host = "0.0.0.0";
                } else if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                    host = HostUtils.getLocalHostName();
                } else if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                    host = HostUtils.getLocalIp();
                }
            }

            // calculate the url to the rest service
            String path = getPath();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            // there may be an optional context path configured to help Camel calculate the correct urls for the REST services
            // this may be needed when using camel-servlet where we cannot get the actual context-path or port number of the servlet engine
            // during init of the servlet
            String contextPath = config.getContextPath();
            if (contextPath != null) {
                if (!contextPath.startsWith("/")) {
                    path = "/" + contextPath + path;
                } else {
                    path = contextPath + path;
                }
            }

            String baseUrl = scheme + "://" + host + (port != 80 ? ":" + port : "") + path;

            String url = baseUrl;
            if (uriTemplate != null) {
                // make sure to avoid double slashes
                if (uriTemplate.startsWith("/")) {
                    url = url + uriTemplate;
                } else {
                    url = url + "/" + uriTemplate;
                }
            }

            Consumer consumer = factory.createConsumer(getCamelContext(), processor, getMethod(), getPath(),
                    getUriTemplate(), getConsumes(), getProduces(), config, getParameters());
            configureConsumer(consumer);

            // add to rest registry so we can keep track of them, we will remove from the registry when the consumer is removed
            // the rest registry will automatic keep track when the consumer is removed,
            // and un-register the REST service from the registry
            getCamelContext().getRestRegistry().addRestService(consumer, url, baseUrl, getPath(), getUriTemplate(), getMethod(),
                    getConsumes(), getProduces(), getInType(), getOutType(), getRouteId(), getDescription());
            return consumer;
        } else {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
