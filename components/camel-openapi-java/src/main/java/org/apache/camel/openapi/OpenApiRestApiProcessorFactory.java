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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestApiProcessorFactory;
import org.apache.camel.spi.RestConfiguration;

public class OpenApiRestApiProcessorFactory implements RestApiProcessorFactory {

    @Override
    public Processor createApiProcessor(CamelContext camelContext, String contextPath, String contextIdPattern, boolean contextIdListing,
                                        RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        Map<String, Object> options = new HashMap<>(parameters);
        if (configuration.getApiProperties() != null) {
            options.putAll(configuration.getApiProperties());
        }

        // need to include host in options
        String host = (String) options.get("host");
        if (host != null) {
            options.put("host", host);
        } else {
            // favor using explicit configured host for the api
            host = configuration.getApiHost();
            if (host != null) {
                options.put("host", host);
            } else {
                host = configuration.getHost();
                int port = configuration.getPort();
                if (host != null && port > 0) {
                    options.put("host", host + ":" + port);
                } else if (host != null) {
                    options.put("host", host);
                } else {
                    options.put("host", "localhost");
                }
            }
        }
        // and include the default scheme as well if not explicit configured
        if (!options.containsKey("schemes") && !options.containsKey("schemas")) {
            // NOTE schemas is a typo but kept for backwards compatible
            String scheme = configuration.getScheme();
            if (scheme != null) {
                options.put("schemes", scheme);
            }
        }
        // and context path is the base.path
        String path = configuration.getContextPath();
        if (path != null) {
            options.put("base.path", path);
        }

        // is cors enabled?
        Object cors = options.get("cors");
        if (cors == null && configuration.isEnableCORS()) {
            options.put("cors", "true");
        }

        return new RestOpenApiProcessor(contextIdPattern, contextIdListing, options, configuration);
    }
}
