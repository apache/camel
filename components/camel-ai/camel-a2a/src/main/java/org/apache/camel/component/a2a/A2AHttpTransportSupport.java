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
package org.apache.camel.component.a2a;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.slf4j.Logger;

final class A2AHttpTransportSupport {

    private A2AHttpTransportSupport() {
    }

    static void handleCorsPreFlight(Exchange exchange) {
        exchange.getMessage().setBody("");
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
    }

    static void setCorsHeaders(Exchange exchange, Map<String, String> corsHeaders, Logger log) {
        String allowOrigin = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Origin") : null;
        if (allowOrigin == null) {
            allowOrigin = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN;
        }
        String allowMethods = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Methods") : null;
        if (allowMethods == null) {
            allowMethods = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS;
        }
        String allowHeaders = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Headers") : null;
        if (allowHeaders == null) {
            allowHeaders = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS
                           + ", A2A-Version, A2A-Extensions, Authorization";
        }
        String maxAge = corsHeaders != null ? corsHeaders.get("Access-Control-Max-Age") : null;
        if (maxAge == null) {
            maxAge = RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE;
        }
        String allowCredentials = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Credentials") : null;

        if ("true".equalsIgnoreCase(allowCredentials) && "*".equals(allowOrigin)) {
            log.warn("Ignoring Access-Control-Allow-Credentials=true with wildcard CORS origin for A2A endpoint");
            allowCredentials = null;
        }

        exchange.getMessage().setHeader("Access-Control-Allow-Origin", allowOrigin);
        exchange.getMessage().setHeader("Access-Control-Allow-Methods", allowMethods);
        exchange.getMessage().setHeader("Access-Control-Allow-Headers", allowHeaders);
        exchange.getMessage().setHeader("Access-Control-Max-Age", maxAge);
        if (allowCredentials != null) {
            exchange.getMessage().setHeader("Access-Control-Allow-Credentials", allowCredentials);
        }
    }

    static RestConsumerFactory resolveRestConsumerFactory(A2AEndpoint endpoint, Logger log) {
        String serverComponent = endpoint.getConfiguration().getHttpServerComponent();
        if (serverComponent != null) {
            return lookupNamedRestConsumerFactory(endpoint, serverComponent, "httpServerComponent");
        }

        RestConfiguration restConfig = endpoint.getCamelContext().getRestConfiguration();
        String restComponent = restConfig != null ? restConfig.getComponent() : null;
        if (restComponent != null) {
            return lookupNamedRestConsumerFactory(endpoint, restComponent, "Configured REST component");
        }

        Component platformHttp = endpoint.getCamelContext().getComponent("platform-http", false);
        if (platformHttp instanceof RestConsumerFactory rcf) {
            log.debug("Auto discovered platform-http as RestConsumerFactory");
            return rcf;
        }

        Set<RestConsumerFactory> componentFactories = new LinkedHashSet<>();
        for (String name : endpoint.getCamelContext().getComponentNames()) {
            Component component = endpoint.getCamelContext().getComponent(name, false);
            if (component instanceof RestConsumerFactory rcf) {
                componentFactories.add(rcf);
            }
        }
        if (componentFactories.size() == 1) {
            return componentFactories.iterator().next();
        }
        if (componentFactories.size() > 1) {
            log.debug("Skipping component RestConsumerFactory auto-discovery because {} factories are registered",
                    componentFactories.size());
        }

        Set<RestConsumerFactory> factories = endpoint.getCamelContext()
                .getRegistry().findByType(RestConsumerFactory.class);
        if (factories.size() == 1) {
            return factories.iterator().next();
        }
        if (factories.size() > 1) {
            log.debug("Skipping registry RestConsumerFactory auto-discovery because {} factories are registered",
                    factories.size());
        }

        return null;
    }

    private static RestConsumerFactory lookupNamedRestConsumerFactory(A2AEndpoint endpoint, String name, String source) {
        Object bean = endpoint.getCamelContext().getRegistry().lookupByName(name);
        if (bean instanceof RestConsumerFactory rcf) {
            return rcf;
        }
        Component component = endpoint.getCamelContext().getComponent(name, true);
        if (component instanceof RestConsumerFactory rcf) {
            return rcf;
        }
        throw new IllegalArgumentException(source + " '" + name + "' does not implement RestConsumerFactory");
    }
}
