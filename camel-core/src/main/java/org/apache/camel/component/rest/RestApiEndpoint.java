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
package org.apache.camel.component.rest;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestApiProcessorFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "rest-api", title = "REST API", syntax = "rest-api:path", consumerOnly = true, label = "core,rest")
public class RestApiEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private String path;
    @UriParam
    private String componentName;

    private Map<String, Object> parameters;

    public RestApiEndpoint(String endpointUri, RestApiComponent component) {
        super(endpointUri, component);
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

    public String getComponentName() {
        return componentName;
    }

    /**
     * The Camel Rest component to use for the REST transport, such as restlet, spark-rest.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory is registered in the registry.
     * If either one is found, then that is being used.
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
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

        // lookup in registry
        Set<RestApiProcessorFactory> factories = getCamelContext().getRegistry().findByType(RestApiProcessorFactory.class);
        if (factories != null && factories.size() == 1) {
            factory = factories.iterator().next();
        }

        if (factory != null) {

            RestConfiguration config = getCamelContext().getRestConfiguration(componentName, true);

            // calculate the url to the rest API service
            String path = getPath();
            if (path != null && !path.startsWith("/")) {
                path = "/" + path;
            }

            Processor processor = factory.createApiProcessor(getCamelContext(), path, config, getParameters());
            return new RestApiProducer(this, processor);
        } else {
            throw new IllegalStateException("Cannot find RestApiProcessorFactory in Registry");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestApiConsumerFactory factory = null;
        String cname = null;
        if (getComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getComponentName());
            if (comp != null && comp instanceof RestApiConsumerFactory) {
                factory = (RestApiConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getComponentName());
                if (comp != null && comp instanceof RestApiConsumerFactory) {
                    factory = (RestApiConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + getComponentName() + " is not a RestApiConsumerFactory");
                } else {
                    throw new NoSuchBeanException(getComponentName(), RestApiConsumerFactory.class.getName());
                }
            }
            cname = getComponentName();
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp != null && comp instanceof RestApiConsumerFactory) {
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

            RestConfiguration config = getCamelContext().getRestConfiguration(cname, true);

            // calculate the url to the rest API service
            String path = getPath();
            if (path != null && !path.startsWith("/")) {
                path = "/" + path;
            }

            // TODO: is this needed?
            // there may be an optional context path configured to help Camel calculate the correct urls for the REST services
            // this may be needed when using camel-serlvet where we cannot get the actual context-path or port number of the servlet engine
            // during init of the servlet
/*            String contextPath = config.getApiContextPath();
            if (contextPath != null) {
                if (!contextPath.startsWith("/")) {
                    path = "/" + contextPath + path;
                } else {
                    path = contextPath + path;
                }
            }
*/
            Consumer consumer = factory.createApiConsumer(getCamelContext(), processor, path, config, getParameters());
            configureConsumer(consumer);

            return consumer;
        } else {
            throw new IllegalStateException("Cannot find RestApiConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
