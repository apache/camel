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
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class RestOpenApiProcessor extends ServiceSupport implements Processor, CamelContextAware {

    private final RestApiResponseAdapter jsonAdapter = new DefaultRestApiResponseAdapter();
    private final RestApiResponseAdapter yamlAdapter = new DefaultRestApiResponseAdapter();
    private CamelContext camelContext;
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
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        // optimize if not using forward headers
        if (!configuration.isUseXForwardHeaders()) {
            StartupStepRecorder recorder = camelContext.getCamelContextExtension().getStartupStepRecorder();
            StartupStep step = recorder.beginStep(RestOpenApiProcessor.class, "openapi", "Generating OpenAPI specification");
            try {
                support.renderResourceListing(camelContext, jsonAdapter, openApiConfig, true,
                        camelContext.getClassResolver(), configuration, null);
                yamlAdapter.setOpenApi(jsonAdapter.getOpenApi()); // no need to compute OpenApi again
                support.renderResourceListing(camelContext, yamlAdapter, openApiConfig, false,
                        camelContext.getClassResolver(), configuration, null);
            } finally {
                recorder.endStep(step);
            }
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!isRunAllowed()) {
            throw new RejectedExecutionException();
        }

        String route = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        String accept = exchange.getIn().getHeader("Accept", String.class);

        // whether to use json or yaml
        boolean json = false;
        boolean yaml = false;
        if (route != null && route.endsWith(".json")) {
            json = true;
        } else if (route != null && route.endsWith(".yaml")) {
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

        RestApiResponseAdapter adapter;
        if (configuration.isUseXForwardHeaders()) {
            // re-create api as using x-forward headers impacts the rendered output
            adapter = new DefaultRestApiResponseAdapter();
            support.renderResourceListing(camelContext, adapter, openApiConfig, json,
                    camelContext.getClassResolver(), configuration, exchange);
        } else {
            // use pre-build adapter
            adapter = json ? jsonAdapter : yamlAdapter;
        }
        adapter.copyResult(exchange);
    }

}
