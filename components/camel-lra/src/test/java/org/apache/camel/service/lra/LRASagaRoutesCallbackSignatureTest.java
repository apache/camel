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

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.service.lra.LRAConstants.DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPENSATION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPLETION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_LRA_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_SIGNATURE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the participant callback routes when a callback secret is configured on the LRA saga service.
 */
class LRASagaRoutesCallbackSignatureTest extends CamelTestSupport {

    private static final String CALLBACK_SECRET = "a-callback-secret";
    // the LRA a callback URL is signed for, and another one named by the requests
    private static final String LRA = "http://localhost/lra-coordinator/0_1";
    private static final String REQUEST_LRA = "http://localhost/lra-coordinator/0_2";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final LRACallbackSigner signer = new LRACallbackSigner(CALLBACK_SECRET);
    private final LRASagaService sagaService = new LRASagaService();
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // the tests never start a saga, so the coordinator is not called
        sagaService.setCoordinatorUrl("http://localhost:1");
        sagaService.setLocalParticipantUrl("http://localhost:" + port.getPort());
        sagaService.setCallbackSecret(CALLBACK_SECRET);
        context.addService(sagaService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().port(port.getPort());
            }
        });
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // registers the compensation and completion endpoints of the saga step
                from("direct:order")
                        .saga().propagation(SagaPropagation.SUPPORTS)
                        .option("orderId", header("orderId"))
                        .compensation("mock:cancel")
                        .completion("mock:confirm")
                        .to("mock:order");
            }
        };
    }

    @Test
    void signedCompensationIsRouted() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(1);
        cancel.expectedHeaderReceived("orderId", "42");
        cancel.expectedHeaderReceived(Exchange.SAGA_LONG_RUNNING_ACTION, LRA);

        assertEquals(200,
                callback(PARTICIPANT_PATH_COMPENSATE, signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42"))));

        cancel.assertIsSatisfied();
    }

    @Test
    void signedCompletionIsRouted() throws Exception {
        MockEndpoint confirm = getMockEndpoint("mock:confirm");
        confirm.expectedMessageCount(1);
        confirm.expectedHeaderReceived("orderId", "42");
        confirm.expectedHeaderReceived(Exchange.SAGA_LONG_RUNNING_ACTION, LRA);

        assertEquals(200,
                callback(PARTICIPANT_PATH_COMPLETE, signedQuery(PARTICIPANT_PATH_COMPLETE, parameters("42"))));

        confirm.assertIsSatisfied();
    }

    @Test
    void callbackWithoutSignatureIsRejected() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(0);

        assertEquals(403, callback(PARTICIPANT_PATH_COMPENSATE, query(parameters("42"))));

        cancel.assertIsSatisfied();
    }

    @Test
    void callbackWithChangedOptionIsRejected() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(0);
        String signed = signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42"));

        assertEquals(403, callback(PARTICIPANT_PATH_COMPENSATE, signed.replace("orderId=42", "orderId=43")));

        cancel.assertIsSatisfied();
    }

    @Test
    void compensationSignatureIsRejectedForCompletion() throws Exception {
        MockEndpoint confirm = getMockEndpoint("mock:confirm");
        confirm.expectedMessageCount(0);

        assertEquals(403,
                callback(PARTICIPANT_PATH_COMPLETE, signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42"))));

        confirm.assertIsSatisfied();
    }

    @Test
    void signedValuesTakePrecedenceOverRequestHeaders() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(1);
        cancel.expectedHeaderReceived("orderId", "42");

        assertEquals(200, callback(PARTICIPANT_PATH_COMPENSATE,
                signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42")), "orderId", "43"));

        cancel.assertIsSatisfied();
    }

    @Test
    void signedCallbackToUnregisteredEndpointIsRejected() throws Exception {
        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedMessageCount(0);
        Map<String, String> parameters = parameters("42");
        parameters.put(URL_COMPENSATION_KEY, "mock://other");

        assertEquals(500, callback(PARTICIPANT_PATH_COMPENSATE, signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters)));

        other.assertIsSatisfied();
    }

    @Test
    void callbackForAnotherLraIsRejected() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(0);
        String signed = signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42"));

        assertEquals(403, callback(PARTICIPANT_PATH_COMPENSATE, signed.replace(encode(LRA), encode(REQUEST_LRA))));

        cancel.assertIsSatisfied();
    }

    @Test
    void signedCallbackWithoutLraIsRejected() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(0);
        Map<String, String> parameters = parameters("42");
        parameters.remove(URL_LRA_KEY);

        assertEquals(403, callback(PARTICIPANT_PATH_COMPENSATE, signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters)));

        cancel.assertIsSatisfied();
    }

    @Test
    void signatureIsKeptOutOfTheStepRoute() throws Exception {
        MockEndpoint cancel = getMockEndpoint("mock:cancel");
        cancel.expectedMessageCount(1);

        assertEquals(200,
                callback(PARTICIPANT_PATH_COMPENSATE, signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("42"))));

        cancel.assertIsSatisfied();
        Message received = cancel.getReceivedExchanges().get(0).getIn();
        for (String header : new String[] { Exchange.HTTP_QUERY, Exchange.HTTP_RAW_QUERY }) {
            assertFalse(received.getHeader(header, "", String.class).contains(URL_SIGNATURE_KEY), header);
        }
    }

    @Test
    void rawQueryIsReadBeforeTheDecodedOne() throws Exception {
        // camel-netty-http sets CamelHttpQuery to the decoded query, and CamelHttpRawQuery to the query as sent;
        // the rest-dsl route inlines the verification, so it is called directly, as LRASagaRoutesTest does
        String rawQuery = signedQuery(PARTICIPANT_PATH_COMPENSATE, parameters("4&2=x+y%"));
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, REQUEST_LRA);
        exchange.getIn().setHeader(Exchange.HTTP_RAW_QUERY, rawQuery);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, URLDecoder.decode(rawQuery, StandardCharsets.UTF_8));

        Method verify = LRASagaRoutes.class.getDeclaredMethod("verifyCompensationRequest", Exchange.class);
        verify.setAccessible(true);
        verify.invoke(new LRASagaRoutes(sagaService), exchange);

        assertNull(exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("4&2=x+y%", exchange.getIn().getHeader("orderId"));
        assertEquals(LRA, exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION));
    }

    private int callback(String path, String query, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port.getPort() + DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH + path + "?" + query))
                .header(Exchange.SAGA_LONG_RUNNING_ACTION, REQUEST_LRA)
                .PUT(HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String signedQuery(String path, Map<String, String> parameters) {
        return query(parameters) + "&" + URL_SIGNATURE_KEY + "=" + signer.sign(path, parameters);
    }

    private static String query(Map<String, String> parameters) {
        StringJoiner query = new StringJoiner("&");
        parameters.forEach((name, value) -> query.add(encode(name) + "=" + encode(value)));
        return query.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parameters(String orderId) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("orderId", orderId);
        parameters.put(URL_COMPENSATION_KEY, "mock://cancel");
        parameters.put(URL_COMPLETION_KEY, "mock://confirm");
        parameters.put(URL_LRA_KEY, LRA);
        return parameters;
    }
}
