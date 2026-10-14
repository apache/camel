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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.service.lra.LRAConstants.*;

public class LRASagaRoutes extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LRASagaRoutes.class);

    private final LRASagaService sagaService;

    public LRASagaRoutes(LRASagaService sagaService) {
        this.sagaService = sagaService;
    }

    @Override
    public void configure() throws Exception {

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPENSATE).to("direct:lra-compensation");
        from("direct:lra-compensation").routeId("lra-compensation")
                .process(this::verifyCompensationRequest)
                .choice()
                .when(header(URL_COMPENSATION_KEY).isNotNull())
                .toD("${header." + URL_COMPENSATION_KEY + "}")
                .end();

        rest(sagaService.getLocalParticipantContextPath())
                .put(PARTICIPANT_PATH_COMPLETE).to("direct:lra-completion");
        from("direct:lra-completion").routeId("lra-completion")
                .process(this::verifyCompletionRequest)
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
            result = Arrays.stream(queryStr.split("&")).collect(
                    Collectors.toMap(element -> decode(saveArrayAccess(element.split("=", 2), 0)),
                            element -> decode(saveArrayAccess(element.split("=", 2), 1))));

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

    private void verifyCompensationRequest(Exchange exchange) {
        verifyRequest(exchange, true);
    }

    private void verifyCompletionRequest(Exchange exchange) {
        verifyRequest(exchange, false);
    }

    /**
     * Check if the request is pointing to an allowed URI to prevent unauthorized remote uri invocation. Validates only
     * the URI relevant to the callback type against the corresponding allowlist. When a callback secret is configured,
     * the signature of the called URL is verified first.
     */
    private void verifyRequest(Exchange exchange, boolean isCompensation) {
        if (exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) == null) {
            throw new IllegalArgumentException("Missing " + Exchange.SAGA_LONG_RUNNING_ACTION + " header in received request");
        }

        LRACallbackSigner signer = sagaService.getCallbackSigner();
        if (signer != null) {
            // the signed parameters are the only source of the step URIs: no fallback to the query below
            if (applySignedParameters(exchange, signer, isCompensation)) {
                checkAllowed(exchange.getIn().getHeader(isCompensation ? URL_COMPENSATION_KEY : URL_COMPLETION_KEY,
                        String.class), isCompensation);
            }
            return;
        }

        String compensationURI = exchange.getIn().getHeader(URL_COMPENSATION_KEY, String.class);
        String completionURI = exchange.getIn().getHeader(URL_COMPLETION_KEY, String.class);

        // CAMEL-17751: Extract URIs from the CamelHttpQuery header
        if (compensationURI == null && completionURI == null) {
            Map<String, String> queryParams
                    = parseQuery(exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class));

            if (!queryParams.isEmpty()) {
                if (queryParams.get(URL_COMPENSATION_KEY) != null) {
                    compensationURI = queryParams.get(URL_COMPENSATION_KEY);
                    exchange.getIn().setHeader(URL_COMPENSATION_KEY, compensationURI);
                }
                if (queryParams.get(URL_COMPLETION_KEY) != null) {
                    completionURI = queryParams.get(URL_COMPLETION_KEY);
                    exchange.getIn().setHeader(URL_COMPLETION_KEY, completionURI);
                }
            }
        }

        checkAllowed(isCompensation ? compensationURI : completionURI, isCompensation);
    }

    private void checkAllowed(String uri, boolean isCompensation) {
        if (uri != null) {
            Set<String> allowedURIs = isCompensation
                    ? sagaService.getRegisteredCompensationURIs()
                    : sagaService.getRegisteredCompletionURIs();
            if (!allowedURIs.contains(uri)) {
                throw new IllegalArgumentException("URI " + uri + " is not allowed");
            }
        }
    }

    /**
     * Verifies the signature of the called URL, and sets the step URIs and the saga options from its query parameters,
     * so that only signed values reach the step endpoint. A callback with a missing or invalid signature is answered
     * with HTTP 403 and not routed any further.
     *
     * @return true if the signature is valid
     */
    private boolean applySignedParameters(Exchange exchange, LRACallbackSigner signer, boolean isCompensation) {
        Message message = exchange.getIn();
        String callbackPath = isCompensation ? PARTICIPANT_PATH_COMPENSATE : PARTICIPANT_PATH_COMPLETE;

        // the raw query first, as some consumers, such as camel-netty-http, decode CamelHttpQuery
        String query = message.getHeader(Exchange.HTTP_RAW_QUERY, String.class);
        if (query == null) {
            query = message.getHeader(Exchange.HTTP_QUERY, String.class);
        }
        Map<String, String> parameters;
        try {
            parameters = parseQuery(query);
        } catch (IllegalStateException e) {
            // a repeated parameter, which a URL enlisted by the participant never has
            parameters = null;
        }
        String signature = parameters != null ? parameters.remove(URL_SIGNATURE_KEY) : null;

        message.removeHeader(URL_COMPENSATION_KEY);
        message.removeHeader(URL_COMPLETION_KEY);
        message.removeHeader(URL_SIGNATURE_KEY);
        if (!signer.verify(callbackPath, parameters, signature) || parameters.get(URL_LRA_KEY) == null) {
            LOG.warn("Rejected a call to the LRA participant endpoint {} without a valid signature", callbackPath);
            message.setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
            message.setBody(null);
            exchange.setRouteStop(true);
            return false;
        }
        // the step works on the LRA the URL was signed for, not on the one named by the request
        message.setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, parameters.remove(URL_LRA_KEY));
        parameters.forEach(message::setHeader);
        // keep the signature out of the step route
        removeSignature(message, Exchange.HTTP_RAW_QUERY);
        removeSignature(message, Exchange.HTTP_QUERY);
        return true;
    }

    private static void removeSignature(Message message, String queryHeader) {
        String query = message.getHeader(queryHeader, String.class);
        if (query != null) {
            message.setHeader(queryHeader, Arrays.stream(query.split("&"))
                    .filter(parameter -> !parameter.startsWith(URL_SIGNATURE_KEY + "="))
                    .collect(Collectors.joining("&")));
        }
    }

}
