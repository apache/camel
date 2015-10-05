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

import java.util.Collections;
import java.util.Map;

import io.swagger.jaxrs.config.BeanConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestSwaggerProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerProcessor.class);
    private final BeanConfig swaggerConfig;
    private final RestSwaggerSupport support;
    private final String contextIdPattern;
    private final boolean contextIdListing;

    @SuppressWarnings("unchecked")
    public RestSwaggerProcessor(String contextIdPattern, boolean contextIdListing, Map<String, Object> parameters) {
        this.contextIdPattern = contextIdPattern;
        this.contextIdListing = contextIdListing;
        this.support = new RestSwaggerSupport();
        this.swaggerConfig = new BeanConfig();

        if (parameters == null) {
            parameters = Collections.EMPTY_MAP;
        }
        support.initSwagger(swaggerConfig, parameters);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String contextId = exchange.getContext().getName();
        String route = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);

        RestApiResponseAdapter adapter = new ExchangeRestApiResponseAdapter(exchange);

        try {
            // render list of camel contexts as root
            if (contextIdListing && (ObjectHelper.isEmpty(route) || route.equals("/"))) {
                support.renderCamelContexts(adapter, contextId, contextIdPattern);
            } else {
                String name;
                if (ObjectHelper.isNotEmpty(route)) {
                    // first part is the camel context
                    if (route.startsWith("/")) {
                        route = route.substring(1);
                    }
                    // the remainder is the route part
                    name = route.split("/")[0];
                    if (route.startsWith(contextId)) {
                        route = route.substring(name.length());
                    }
                } else {
                    // listing not enabled then get current camel context as the name
                    name = exchange.getContext().getName();
                }

                boolean match = true;
                if (contextIdPattern != null) {
                    if ("#name#".equals(contextIdPattern)) {
                        match = name.equals(contextId);
                    } else {
                        match = EndpointHelper.matchPattern(name, contextIdPattern);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Match contextId: {} with pattern: {} -> {}", new Object[]{name, contextIdPattern, match});
                    }
                }

                if (!match) {
                    adapter.noContent();
                } else {
                    support.renderResourceListing(adapter, swaggerConfig, name, route, exchange.getContext().getClassResolver());
                }
            }
        } catch (Exception e) {
            LOG.warn("Error rendering Swagger API due " + e.getMessage(), e);
        }
    }

}
