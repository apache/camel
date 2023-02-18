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
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestApiProcessorFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * Expose OpenAPI Specification of the REST services defined using Camel REST DSL.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "rest-api", title = "REST API", syntax = "rest-api:path",
             consumerOnly = true, category = { Category.CORE, Category.REST }, lenientProperties = true)
public class RestApiEndpoint extends DefaultEndpoint {

    public static final String DEFAULT_API_COMPONENT_NAME = "openapi";
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/restapi/";

    @UriPath
    @Metadata(required = true)
    private String path;
    @UriParam
    private String consumerComponentName;
    @UriParam
    private String apiComponentName;

    private Map<String, Object> parameters;

    public RestApiEndpoint(String endpointUri, RestApiComponent component) {
        super(endpointUri, component);
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public RestApiComponent getComponent() {
        return (RestApiComponent) super.getComponent();
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

    public String getApiComponentName() {
        return apiComponentName;
    }

    /**
     * The Camel Rest API component to use for generating the API of the REST services, such as openapi.
     */
    public void setApiComponentName(String apiComponentName) {
        this.apiComponentName = apiComponentName;
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

    @Override
    public Producer createProducer() throws Exception {
        RestApiProcessorFactory factory = null;

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), getConsumerComponentName());

        // lookup in registry
        Set<RestApiProcessorFactory> factories = getCamelContext().getRegistry().findByType(RestApiProcessorFactory.class);
        if (factories != null && factories.size() == 1) {
            factory = factories.iterator().next();
        }

        // lookup on classpath using factory finder to automatic find it (just add camel-openapi-java to classpath etc)
        if (factory == null) {
            String name = apiComponentName != null ? apiComponentName : config.getApiComponent();
            if (name == null) {
                name = DEFAULT_API_COMPONENT_NAME; //use openapi first
            }
            FactoryFinder finder = getCamelContext().getCamelContextExtension().getFactoryFinder(RESOURCE_PATH);
            factory = finder.newInstance(name, RestApiProcessorFactory.class).orElse(null);
        }

        if (factory == null) {
            String name = apiComponentName != null ? apiComponentName : config.getApiComponent();
            if (name == null) {
                name = "swagger"; //use swagger as fallback
            }
            FactoryFinder finder = getCamelContext().getCamelContextExtension().getFactoryFinder(RESOURCE_PATH);
            factory = finder.newInstance(name, RestApiProcessorFactory.class).orElse(null);
        }

        if (factory != null) {

            // if no explicit port/host configured, then use port from rest configuration
            String host = "";
            int port = 80;

            if (config.getApiHost() != null) {
                host = config.getApiHost();
            } else if (config.getHost() != null) {
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

                // no host was configured so calculate a host to use
                // there should be no schema in the host (but only port)
                String targetHost = host + (port != 80 ? ":" + port : "");
                getParameters().put("host", targetHost);
            }

            // the base path should start with a leading slash
            String path = getPath();
            if (path != null && !path.startsWith("/")) {
                path = "/" + path;
            }

            Processor processor = factory.createApiProcessor(getCamelContext(), path, config, getParameters());
            return new RestApiProducer(this, processor);
        } else {
            throw new IllegalStateException(
                    "Cannot find RestApiProcessorFactory in Registry or classpath (such as the camel-openapi-java component)");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestApiConsumerFactory factory = null;
        String cname = null;

        // we use the rest component as the HTTP consumer to service the API
        // the API then uses the api component (eg usually camel-openapi-java) to build the API
        if (getConsumerComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getConsumerComponentName());
            if (comp instanceof RestApiConsumerFactory) {
                factory = (RestApiConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getConsumerComponentName());
                if (comp instanceof RestApiConsumerFactory) {
                    factory = (RestApiConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException(
                            "Component " + getConsumerComponentName() + " is not a RestApiConsumerFactory");
                } else {
                    throw new NoSuchBeanException(getConsumerComponentName(), RestApiConsumerFactory.class.getName());
                }
            }
            cname = getConsumerComponentName();
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp instanceof RestApiConsumerFactory) {
                    factory = (RestApiConsumerFactory) comp;
                    cname = name;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestApiConsumerFactory> factories = getCamelContext().getRegistry().findByType(RestApiConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory != null) {
            // calculate the url to the rest API service
            RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), cname);

            // calculate the url to the rest API service
            String path = getPath();
            if (path != null && !path.startsWith("/")) {
                path = "/" + path;
            }

            Consumer consumer = factory.createApiConsumer(getCamelContext(), processor, path, config, getParameters());
            configureConsumer(consumer);

            return consumer;
        } else {
            throw new IllegalStateException("Cannot find RestApiConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
