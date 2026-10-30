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
package org.apache.camel.coap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.interceptors.MessageInterceptorAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer must fail the exchange when the CoAP server does not answer, instead of completing it with the request
 * body.
 */
public class CoAPProducerNoResponseTest extends CoAPTestSupport {

    // nothing listens on this port
    @RegisterExtension
    static AvailablePortFinder.Port unusedPort = AvailablePortFinder.find();

    @BindToRegistry("noAnswerClient")
    private final CoapClient noAnswerClient = new CoapClient(
            String.format("coap://localhost:%d/TestResource", unusedPort.getPort())).setTimeout(500L);

    @AfterEach
    void shutdownClient() {
        noAnswerClient.shutdown();
    }

    @Test
    void testNoResponseFailsTheExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = template.request("direct:start", e -> e.getIn().setBody("Hello"));

        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, out.getException());
        assertTrue(cause.getMessage().startsWith("No response received from CoAP server"), cause.getMessage());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testStoppingTheProducerWithARequestInFlightFailsTheExchange() throws Exception {
        CoAPEndpoint endpoint = context.getEndpoint(
                String.format("coap://localhost:%d/TestResource", unusedPort.getPort()), CoAPEndpoint.class);
        CoAPProducer producer = (CoAPProducer) endpoint.createProducer();
        producer.start();
        producer.initClient();

        AtomicReference<Request> sentRequest = new AtomicReference<>();
        CountDownLatch requestSent = new CountDownLatch(1);
        producer.getClient().getEndpoint().addInterceptor(new MessageInterceptorAdapter() {
            @Override
            public void sendRequest(Request request) {
                // called before the connector sends the datagram
                request.addMessageObserver(new MessageObserverAdapter() {
                    @Override
                    public void onSent(boolean retransmission) {
                        sentRequest.set(request);
                        requestSent.countDown();
                    }
                });
            }
        });

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(CoAPConstants.COAP_METHOD, CoAPConstants.METHOD_GET);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> inflight = executor.submit(() -> {
                producer.process(exchange);
                return null;
            });
            assertTrue(requestSent.await(5, TimeUnit.SECONDS), "the request was not sent");

            // destroying the client endpoint cancels the request; the requesting thread waits for the response on the
            // monitor of the request, so holding it here lets that thread go on only after doStop has completed
            Request request = sentRequest.get();
            synchronized (request) {
                producer.stop();
            }

            ExecutionException failure = assertThrows(ExecutionException.class, () -> inflight.get(5, TimeUnit.SECONDS));
            CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, failure.getCause());
            assertTrue(cause.getMessage().startsWith("No response received from CoAP server for GET"), cause.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("coap://localhost:%d/TestResource?client=#noAnswerClient", unusedPort.getPort())
                        .to("mock:result");
            }
        };
    }
}
