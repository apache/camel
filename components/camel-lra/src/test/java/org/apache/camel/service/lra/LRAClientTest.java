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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPENSATE;
import static org.apache.camel.service.lra.LRAConstants.PARTICIPANT_PATH_COMPLETE;
import static org.apache.camel.service.lra.LRAConstants.URL_SIGNATURE_KEY;

public class LRAClientTest extends CamelTestSupport {

    public LRAClientTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @DisplayName("Tests whether LRAClient is using a default HttpClient")
    @Test
    void testCanCreateLRAClient() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService);
        Assertions.assertNotNull(client, "client must not be null after initializing");
    }

    @DisplayName("Tests whether LRAClient is using a custom set HttpClient")
    @Test
    void testCanCreateLRAClientWithCustomHttpClient() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService, HttpClient.newBuilder().build());
        Assertions.assertNotNull(client, "client must not be null after initializing");
    }

    @DisplayName("Tests whether LRAClient is throwing an exception if httpclient is null")
    @Test
    void testCannotCreateLRAClientWithoutHttpClient() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        Assertions.assertThrows(IllegalArgumentException.class, () -> new LRAClient(sagaService, null),
                "no client should result in IllegalArgumentException");
    }

    @DisplayName("Tests whether LRAClient is calling prepareRequest with exchange from newLRA()")
    @Test
    void testCallsPrepareRequestWithExchangeInNewLra() {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService) {
            protected HttpRequest.Builder prepareRequest(URI uri, Exchange exchange) {
                throw new ExchangeRuntimeException(exchange);
            }
        };
        Exchange exchange = Mockito.mock(Exchange.class);
        Assertions.assertThrows(ExchangeRuntimeException.class, () -> client.newLRA(exchange));
    }

    @DisplayName("Tests whether LRAClient is calling prepareRequest with exchange from compensate()")
    @Test
    void testCallsPrepareRequestWithExchangeInCompensate() throws MalformedURLException {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService) {
            protected HttpRequest.Builder prepareRequest(URI uri, Exchange exchange) {
                throw new ExchangeRuntimeException(exchange);
            }
        };
        Exchange exchange = Mockito.mock(Exchange.class);
        Assertions.assertThrows(ExchangeRuntimeException.class,
                () -> client.compensate(URI.create("https://localhost/saga").toURL(), exchange));
    }

    @DisplayName("Tests whether LRAClient is calling prepareRequest with exchange from complete()")
    @Test
    void testCallsPrepareRequestWithExchangeInComplete() throws MalformedURLException {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService) {
            protected HttpRequest.Builder prepareRequest(URI uri, Exchange exchange) {
                throw new ExchangeRuntimeException(exchange);
            }
        };
        Exchange exchange = Mockito.mock(Exchange.class);
        Assertions.assertThrows(ExchangeRuntimeException.class,
                () -> client.complete(URI.create("https://localhost/saga").toURL(), exchange));
    }

    @DisplayName("Tests prepare request works without exchange")
    @Test
    void testPrepareRequestWithoutExchange() throws Exception {
        LRASagaService sagaService = new LRASagaService();
        applyMockProperties(sagaService);
        LRAClient client = new LRAClient(sagaService);
        URI uri = new URI("https://lcoalhost/someURI");
        HttpRequest.Builder expected = HttpRequest.newBuilder(uri);
        HttpRequest.Builder actual = client.prepareRequest(uri, null);

        Assertions.assertEquals(actual.build(), expected.build());
    }

    @DisplayName("Tests whether join() signs the participant callback URLs when a callback secret is set")
    @Test
    void testJoinSignsCallbackUrlsWithCallbackSecret() throws Exception {
        Map<String, Map<String, String>> callbacks = joinAndCaptureCallbacks("a-callback-secret");

        LRACallbackSigner signer = new LRACallbackSigner("a-callback-secret");
        for (String path : new String[] { PARTICIPANT_PATH_COMPENSATE, PARTICIPANT_PATH_COMPLETE }) {
            Map<String, String> parameters = callbacks.get(path);
            Assertions.assertEquals("42", parameters.get("id"), "saga option must be in the " + path + " URL");
            String signature = parameters.remove(URL_SIGNATURE_KEY);
            Assertions.assertTrue(signer.verify(path, parameters, signature),
                    "the " + path + " URL must carry a valid signature");
        }
    }

    @DisplayName("Tests whether join() leaves the participant callback URLs unsigned without a callback secret")
    @Test
    void testJoinDoesNotSignCallbackUrlsWithoutCallbackSecret() throws Exception {
        Map<String, Map<String, String>> callbacks = joinAndCaptureCallbacks(null);

        for (Map<String, String> parameters : callbacks.values()) {
            Assertions.assertEquals("42", parameters.get("id"), "saga option must be in the callback URL");
            Assertions.assertFalse(parameters.containsKey(URL_SIGNATURE_KEY), "callback URL must not be signed");
        }
    }

    /**
     * Joins an LRA through a mocked HTTP client, and returns the query parameters of the enlisted callback URLs, by
     * participant path.
     */
    private Map<String, Map<String, String>> joinAndCaptureCallbacks(String callbackSecret) throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            LRASagaService sagaService = new LRASagaService() {
                @Override
                public ScheduledExecutorService getExecutorService() {
                    return executor;
                }
            };
            applyMockProperties(sagaService);
            sagaService.setCallbackSecret(callbackSecret);

            HttpClient httpClient = Mockito.mock(HttpClient.class);
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = Mockito.mock(HttpResponse.class);
            Mockito.when(response.statusCode()).thenReturn(200);
            ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
            Mockito.when(httpClient.sendAsync(request.capture(), Mockito.<HttpResponse.BodyHandler<String>> any()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            Map<String, Expression> options = new HashMap<>();
            options.put("id", ExpressionBuilder.constantExpression("42"));
            CamelSagaStep step = new CamelSagaStep(
                    context().getEndpoint("direct:cancelOrder"), context().getEndpoint("direct:confirmOrder"), options, null);
            Exchange exchange = new DefaultExchange(context());

            new LRAClient(sagaService, httpClient)
                    .join(URI.create("http://localhost/lra-coordinator/0_1").toURL(),
                            LRASagaStep.fromCamelSagaStep(step, exchange), exchange)
                    .get(10, TimeUnit.SECONDS);

            String link = request.getValue().headers().firstValue("Link").orElseThrow();
            Map<String, Map<String, String>> callbacks = new HashMap<>();
            Matcher matcher = Pattern.compile("<[^>?]+(/[a-z]+)\\?([^>]*)>; rel=").matcher(link);
            while (matcher.find()) {
                callbacks.put(matcher.group(1), parseQuery(matcher.group(2)));
            }
            Assertions.assertEquals(2, callbacks.size(), "the Link header must hold the compensate and complete URLs");
            return callbacks;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> parameters = new HashMap<>();
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            parameters.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private void applyMockProperties(LRASagaService sagaService) {
        sagaService.setCoordinatorUrl("mockCoordinatorUrl");
        sagaService.setLocalParticipantUrl("mockLocalParticipantUrl");
        sagaService.setLocalParticipantContextPath("mockLocalParticipantContextPath");
        sagaService.setCoordinatorContextPath("mockCoordinatorContextPath");
    }

    private static class ExchangeRuntimeException extends RuntimeException {
        private Exchange exchange;

        public ExchangeRuntimeException(Exchange exchange) {
            this.exchange = exchange;
        }

        public Exchange get() {
            return exchange;
        }
    }
}
