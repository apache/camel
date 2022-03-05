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
package org.apache.camel.service.lra;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPENSATION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPLETION_KEY;

public class LRASagaRoutes extends RouteBuilder {

    private LRASagaService sagaService;

    public LRASagaRoutes(LRASagaService sagaService) {
        this.sagaService = sagaService;
    }

    @Override
    public void configure() throws Exception {

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPENSATE)
                .route().id("lra-compensation")
                .process(this::verifyRequest)
                .choice()
                .when(header(URL_COMPENSATION_KEY).isNotNull())
                .toD("${header." + URL_COMPENSATION_KEY + "}")
                .end();

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPLETE)
                .route().id("lra-completion")
                .process(this::verifyRequest)
                .choice()
                .when(header(URL_COMPLETION_KEY).isNotNull())
                .toD("${header." + URL_COMPLETION_KEY + "}")
                .end();

    }

    /**
     * Check if the request is pointing to an allowed URI to prevent unauthorized
     * remote uri invocation
     */
    private void verifyRequest(Exchange exchange) {
        if (exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) == null) {
            throw new IllegalArgumentException("Missing " + Exchange.SAGA_LONG_RUNNING_ACTION + " header in received request");
        }

        Set<String> usedURIs = new HashSet<>();
        String compensationURI = exchange.getIn().getHeader(URL_COMPENSATION_KEY, String.class);
        if (compensationURI != null) {
            usedURIs.add(compensationURI);
        }
        String completionURI = exchange.getIn().getHeader(URL_COMPLETION_KEY, String.class);
        if (completionURI != null) {
            usedURIs.add(completionURI);
        }

        if (usedURIs.isEmpty()) {
            Map<String, String> queryParams = extractQueryParamsFromHttpQueryHeader(exchange);
            if (!queryParams.isEmpty()) {
                compensationURI = queryParams.get(URL_COMPENSATION_KEY);
                if (compensationURI != null) {
                    usedURIs.add(compensationURI);
                    exchange.getIn().setHeader(URL_COMPENSATION_KEY, compensationURI);
                }
                completionURI = queryParams.get(URL_COMPLETION_KEY);
                if (completionURI != null) {
                    usedURIs.add(completionURI);
                    exchange.getIn().setHeader(URL_COMPLETION_KEY, completionURI);
                }
            }
        }

        for (String uri : usedURIs) {
            if (!sagaService.getRegisteredURIs().contains(uri)) {
                throw new IllegalArgumentException("URI " + uri + " is not allowed");
            }
        }
    }

    private Map<String, String> extractQueryParamsFromHttpQueryHeader(Exchange exchange) {
        Map<String, String> queryParams = new HashMap<>();

        try {
            String strQueryParams = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
            if (strQueryParams != null) {
                String[] pairs = strQueryParams.split("&");
                for (String pair : pairs) {
                    String[] keyValuePair = pair.split("=");
                    queryParams.put(URLDecoder.decode(keyValuePair[0], StandardCharsets.UTF_8.name()),
                            keyValuePair.length == 2 ? URLDecoder.decode(keyValuePair[1], StandardCharsets.UTF_8.name()) : "");
                }
            }

        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeCamelException("Unsupported encoding exception during " + Exchange.HTTP_QUERY + " header parsing");
        }

        return queryParams;
    }

}
