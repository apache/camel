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
package org.apache.camel.openapi;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestOpenApiProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiProcessor.class);
    private final BeanConfig openApiConfig;
    private final RestOpenApiSupport support;
    private final RestConfiguration configuration;

    public RestOpenApiProcessor(Map<String, Object> parameters,
                                RestConfiguration configuration) {
        this.configuration = configuration;
        this.support = new RestOpenApiSupport();
        this.openApiConfig = new BeanConfig();

        if (parameters == null) {
            parameters = Collections.emptyMap();
        }
        support.initOpenApi(openApiConfig, parameters);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String route = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        String accept = exchange.getIn().getHeader("Accept", String.class);

        RestApiResponseAdapter adapter = new ExchangeRestApiResponseAdapter(exchange);

        // whether to use json or yaml
        boolean json = false;
        boolean yaml = false;
        if (route != null && route.endsWith("/openapi.json")) {
            json = true;
        } else if (route != null && route.endsWith("/openapi.yaml")) {
            yaml = true;
        }
        if (accept != null && !json && !yaml) {
            json = accept.toLowerCase(Locale.US).contains("json");
            yaml = accept.toLowerCase(Locale.US).contains("yaml");
        }
        if (!json && !yaml) {
            // json is default
            json = true;
        }

        try {
            support.renderResourceListing(exchange.getContext(), adapter, openApiConfig, json,
                    exchange.getIn().getHeaders(), exchange.getContext().getClassResolver(), configuration);
        } catch (Exception e) {
            LOG.warn("Error rendering OpenApi API due {}", e.getMessage(), e);
        }
    }

}
