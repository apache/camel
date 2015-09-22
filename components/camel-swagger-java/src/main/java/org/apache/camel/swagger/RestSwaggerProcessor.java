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
package org.apache.camel.swagger;

import java.util.Map;
import java.util.Set;

import io.swagger.jaxrs.config.BeanConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestApiResponseAdapter;
import org.apache.camel.spi.RestApiResponseAdapterFactory;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestSwaggerProcessor extends ServiceSupport implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerProcessor.class);
    private final BeanConfig swaggerConfig;
    private final RestSwaggerSupport support;
    private final String componentName;

    public RestSwaggerProcessor(Map<String, Object> parameters) {
        support = new RestSwaggerSupport();
        swaggerConfig = new BeanConfig();
        support.initSwagger(swaggerConfig, parameters);
        componentName = (String) parameters.get("componentName");
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String contextId;
        String route = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);

        try {
            RestApiResponseAdapter adapter = lookupAdapter(exchange);

            // render list of camel contexts as root
            if (route == null || route.equals("") || route.equals("/")) {
                support.renderCamelContexts(adapter);
            } else {
                // first part is the camel context
                if (route.startsWith("/")) {
                    route = route.substring(1);
                }
                // the remainder is the route part
                contextId = route.split("/")[0];
                if (route.startsWith(contextId)) {
                    route = route.substring(contextId.length());
                }

                support.renderResourceListing(adapter, swaggerConfig, contextId, route);
            }
        } catch (Exception e) {
            LOG.warn("Error rendering Swagger API due " + e.getMessage(), e);
        }
    }

    protected RestApiResponseAdapter lookupAdapter(Exchange exchange) {
        CamelContext camelContext = exchange.getContext();

        RestApiResponseAdapterFactory factory = null;

        if (componentName != null) {
            Object comp = camelContext.getRegistry().lookupByName(componentName);
            if (comp != null && comp instanceof RestApiResponseAdapterFactory) {
                factory = (RestApiResponseAdapterFactory) comp;
            } else {
                comp = camelContext.getComponent(componentName);
                if (comp != null && comp instanceof RestApiResponseAdapterFactory) {
                    factory = (RestApiResponseAdapterFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + componentName + " is not a RestApiResponseAdapterFactory");
                } else {
                    throw new NoSuchBeanException(componentName, RestApiResponseAdapterFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : camelContext.getComponentNames()) {
                Component comp = camelContext.getComponent(name);
                if (comp != null && comp instanceof RestApiResponseAdapterFactory) {
                    factory = (RestApiResponseAdapterFactory) comp;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestApiResponseAdapterFactory> factories = camelContext.getRegistry().findByType(RestApiResponseAdapterFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory != null) {
            return factory.newAdapter(exchange);
        } else {
            throw new IllegalStateException("Cannot find RestApiResponseAdapterFactory in Registry or as a Component to use");
        }

    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
