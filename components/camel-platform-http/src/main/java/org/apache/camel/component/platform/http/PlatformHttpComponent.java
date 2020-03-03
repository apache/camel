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
package org.apache.camel.component.platform.http;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.RestComponentHelper;
import org.apache.camel.util.FileUtil;

/**
 * Exposes HTTP endpoints leveraging the given platform's (SpringBoot, WildFly, Quarkus, ...) HTTP server.
 */
@Component("platform-http")
public class PlatformHttpComponent extends DefaultComponent implements RestConsumerFactory, RestApiConsumerFactory {
    @Metadata(label = "advanced", description = "An HTTP Server engine implementation to serve the requests")
    private PlatformHttpEngine engine;

    public PlatformHttpComponent() {
        super();
    }

    public PlatformHttpComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        PlatformHttpEndpoint endpoint = new PlatformHttpEndpoint(uri, remaining, this);
        endpoint.setPlatformHttpEngine(engine);

        return endpoint;
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
            RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration,
                parameters, false);
    }

    public PlatformHttpEngine getEngine() {
        return engine;
    }

    /**
     * Sets the {@link PlatformHttpEngine} to use.
     */
    public PlatformHttpComponent setEngine(PlatformHttpEngine engine) {
        this.engine = engine;
        return this;
    }

    private Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api)
            throws Exception {

        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = configuration;
        if (config == null) {
            config = camelContext.getRestConfiguration(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME, true);
        }

        Map<String, Object> map = RestComponentHelper.initRestEndpointProperties(PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME, config);

        boolean cors = config.isEnableCORS();
        if (cors) {
            // allow HTTP Options as we want to handle CORS in rest-dsl
            map.put("optionsEnabled", "true");
        }
        
        if (api) {
            map.put("matchOnUriPrefix", "true");
        }
        
        RestComponentHelper.addHttpRestrictParam(map, verb, cors);

        // do not append with context-path as the servlet path should be without context-path

        String url = RestComponentHelper.createRestConsumerUrl("platform-http", path, map);

        PlatformHttpEndpoint endpoint = camelContext.getEndpoint(url, PlatformHttpEndpoint.class);
        setProperties(endpoint, parameters);
        endpoint.setConsumes(consumes);
        endpoint.setProduces(produces);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        return consumer;
    }
}
