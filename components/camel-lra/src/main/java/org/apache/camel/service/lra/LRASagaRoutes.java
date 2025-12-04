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

import static org.apache.camel.service.lra.LRAConstants.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LRASagaRoutes extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LRASagaRoutes.class);

    private final LRASagaService sagaService;

    public LRASagaRoutes(LRASagaService sagaService) {
        this.sagaService = sagaService;
    }

    @Override
    public void configure() throws Exception {

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPENSATE)
                .to("direct:lra-compensation");
        from("direct:lra-compensation")
                .routeId("lra-compensation")
                .process(this::verifyRequest)
                .choice()
                .when(header(URL_COMPENSATION_KEY).isNotNull())
                .toD("${header." + URL_COMPENSATION_KEY + "}")
                .end();

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPLETE)
                .to("direct:lra-completion");
        from("direct:lra-completion")
                .routeId("lra-completion")
                .process(this::verifyRequest)
                .choice()
                .when(header(URL_COMPLETION_KEY).isNotNull())
                .toD("${header." + URL_COMPLETION_KEY + "}")
                .end();
    }

    private Map<String, String> parseQuery(String queryStr) {

        Map<String, String> result;

        if (queryStr != null && !queryStr.isEmpty()) {

            // first, split by parameter separator '&'
            // then collect the map with the variable name '[0]' and value '[1]', both url decoded
            result = Arrays.stream(queryStr.split("&"))
                    .collect(Collectors.toMap(
                            element -> decode(saveArrayAccess(element.split("="), 0)),
                            element -> decode(saveArrayAccess(element.split("="), 1))));

        } else {
            LOG.debug("query param is empty, nothing to parse.");
            result = new HashMap<>();
        }

        return result;
    }

    private String saveArrayAccess(String[] keyValuePair, int index) {
        try {
            return keyValuePair[index];
        } catch (Exception ex) {
            LOG.warn("unable to read array index '{}' from '{}'", index, keyValuePair, ex);
            return "";
        }
    }

    private String decode(String encodedString) {
        return URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
    }

    /**
     * Check if the request is pointing to an allowed URI to prevent unauthorized remote uri invocation
     */
    private void verifyRequest(Exchange exchange) {
        if (exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) == null) {
            throw new IllegalArgumentException(
                    "Missing " + Exchange.SAGA_LONG_RUNNING_ACTION + " header in received request");
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

        // CAMEL-17751: Extract URIs from the CamelHttpQuery header
        if (usedURIs.isEmpty()) {
            Map<String, String> queryParams = parseQuery(exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class));

            if (!queryParams.isEmpty()) {

                if (queryParams.get(URL_COMPENSATION_KEY) != null) {
                    compensationURI = queryParams.get(URL_COMPENSATION_KEY);
                    usedURIs.add(compensationURI);
                    exchange.getIn().setHeader(URL_COMPENSATION_KEY, compensationURI);
                }

                if (queryParams.get(URL_COMPLETION_KEY) != null) {
                    completionURI = queryParams.get(URL_COMPLETION_KEY);
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
}
