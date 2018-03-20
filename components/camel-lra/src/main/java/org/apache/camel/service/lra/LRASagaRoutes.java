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
package org.apache.camel.service.lra;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPENSATION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPLETION_KEY;

/**
 *
 */
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
                .toD("header." + URL_COMPENSATION_KEY)
                .end();

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPLETE)
                .route().id("lra-completion")
                .process(this::verifyRequest)
                .choice()
                .when(header(URL_COMPLETION_KEY).isNotNull())
                .toD("header." + URL_COMPLETION_KEY)
                .end();

    }

    /**
     * Check if the request is pointing to an allowed URI to prevent unauthorized remote uri invocation
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

        for (String uri : usedURIs) {
            if (!sagaService.getRegisteredURIs().contains(uri)) {
                throw new IllegalArgumentException("URI " + uri + " is not allowed");
            }
        }
    }

}
