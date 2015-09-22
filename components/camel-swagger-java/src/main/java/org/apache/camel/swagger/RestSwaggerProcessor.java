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

import io.swagger.jaxrs.config.BeanConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestApiResponseAdapter;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestSwaggerProcessor extends ServiceSupport implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerProcessor.class);
    private final BeanConfig swaggerConfig;
    private final RestSwaggerSupport support;

    public RestSwaggerProcessor(Map<String, Object> parameters) {
        support = new RestSwaggerSupport();
        swaggerConfig = new BeanConfig();
        support.initSwagger(swaggerConfig, parameters);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String contextId;
        String route = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);

        RestApiResponseAdapter adapter = null;

        try {

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

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
