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
package org.apache.camel.component.telegram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncProducer;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.apache.camel.test.AvailablePortFinder;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * A request that Telegram answers with an error, with a body that cannot be read, or not at all, must fail the exchange
 * and complete it once, and the calling thread waits for the answer.
 */
class TelegramProducerFailureTest extends TelegramTestSupport {

    // reserved for this test, and nothing listens on it
    @RegisterExtension
    private static final AvailablePortFinder.Port CLOSED_PORT = AvailablePortFinder.find();

    private final HoldingApi venueApi = new HoldingApi();

    @EndpointInject("mock:after")
    private MockEndpoint after;

    @EndpointInject("mock:failed")
    private MockEndpoint failed;

    @Test
    void errorStatusFailsTheExchangeOnce() throws Exception {
        Throwable failure = assertFailsOnce("direct:telegram", telegramUri(), textMessage());

        assertThat(failure).isInstanceOf(RuntimeCamelException.class).hasMessageContaining("responded: 400");
    }

    @Test
    void unreadableAnswerFailsTheExchangeOnce() throws Exception {
        Throwable failure = assertFailsOnce("direct:telegram", telegramUri(), new SendLocationMessage(59.9386292, 30.3141308));

        assertThat(failure).isInstanceOf(RuntimeCamelException.class).hasMessageContaining("Could not parse the response");
    }

    @Test
    void requestWithoutAnswerFailsTheExchangeOnce() throws Exception {
        Throwable failure = assertFailsOnce("direct:closed", closedPortUri(), textMessage());

        assertThat(failure).isInstanceOf(IOException.class);
    }

    @Test
    void errorStatusIsHandledByOnExceptionAndTheRouteDoesNotContinue() {
        Exchange result = template.send("direct:handled", e -> e.getIn().setBody(textMessage()));

        assertSoftly(softly -> {
            softly.assertThat(after.getReceivedCounter()).as("exchanges at the step after the producer").isZero();
            softly.assertThat(failed.getReceivedCounter()).as("exchanges at onException").isEqualTo(1);
            softly.assertThat(result.getException()).as("exception left on the handled exchange").isNull();
        });
    }

    @Test
    void interruptedCallerFailsTheExchangeOnce() throws Exception {
        Completion completion;
        boolean stillInterrupted;
        Thread.currentThread().interrupt();
        try {
            completion = process(telegramUri(), textMessage());
        } finally {
            // also clears the flag for the rest of the test
            stillInterrupted = Thread.interrupted();
        }

        assertSoftly(softly -> completedOnce(softly, completion, true));
        assertThat(stillInterrupted).as("interrupt flag kept").isTrue();
        assertThat(completion.exchange.getException()).isExactlyInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);
    }

    /**
     * The order of the requests is the same without the fix: the calling thread has always waited for the answer, and
     * it must stay so. What is new for an answered message is the thread that completes the exchange.
     */
    @Test
    void twoSendsAreNotInterleavedAndSetTheResult() throws Exception {
        AsyncProducer producer = context.getEndpoint(telegramUri()).createAsyncProducer();
        producer.start();
        try {
            for (int i = 0; i < 2; i++) {
                Completion completion = process(producer, new SendVenueMessage(59.9386292, 30.3141308, "title", "address"));

                assertSoftly(softly -> completedOnce(softly, completion, false));
                assertThat(completion.exchange.getException()).isNull();
                assertThat(completion.exchange.getMessage().getBody()).isInstanceOf(MessageResult.class);
            }
        } finally {
            producer.stop();
        }

        assertThat(venueApi.events).containsExactly("request 1", "answer 1", "request 2", "answer 2");
    }

    /**
     * Sends the message through a route and with the producer alone, and returns the failure that Telegram caused.
     */
    private Throwable assertFailsOnce(String routeUri, String producerUri, Object body) throws Exception {
        Exchange routed = template.send(routeUri, e -> e.getIn().setBody(body));
        Completion completion = process(producerUri, body);

        assertSoftly(softly -> {
            softly.assertThat(after.getReceivedCounter()).as("exchanges at the step after the producer").isZero();
            softly.assertThat(routed.getException()).as("exception of the routed exchange").isNotNull();
            completedOnce(softly, completion, true);
        });
        // the same exception as before, when process() threw it
        Throwable exception = completion.exchange.getException();
        assertThat(exception).isExactlyInstanceOf(RuntimeException.class).hasCauseInstanceOf(ExecutionException.class);
        assertThat(routed.getException()).isExactlyInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(ExecutionException.class);
        return exception.getCause().getCause();
    }

    private static void completedOnce(SoftAssertions softly, Completion completion, boolean failed) {
        softly.assertThat(completion.thrownByProcess).as("thrown by process() of the producer").isNull();
        softly.assertThat(completion.callsWhenProcessReturned).as("calls of the callback when process() returned")
                .isEqualTo(1);
        softly.assertThat(completion.calls.get()).as("calls of the callback").isEqualTo(1);
        softly.assertThat(completion.callingThread.get()).as("thread that called the callback")
                .isSameAs(Thread.currentThread());
        softly.assertThat(completion.failedAtFirstCall.get()).as("exchange failed when it was completed")
                .isEqualTo(failed);
    }

    private Completion process(String uri, Object body) throws Exception {
        AsyncProducer producer = context.getEndpoint(uri).createAsyncProducer();
        producer.start();
        try {
            return process(producer, body);
        } finally {
            producer.stop();
        }
    }

    /**
     * Calls the producer the way a route does and records every call of the callback.
     */
    private static Completion process(AsyncProducer producer, Object body) throws Exception {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getIn().setBody(body);
        Completion completion = new Completion(exchange);
        try {
            producer.process(exchange, doneSync -> {
                if (completion.calls.getAndIncrement() == 0) {
                    completion.callingThread.set(Thread.currentThread());
                    completion.failedAtFirstCall.set(exchange.getException() != null);
                }
                completion.called.countDown();
            });
        } catch (RuntimeException e) {
            completion.thrownByProcess = e;
        }
        completion.callsWhenProcessReturned = completion.calls.get();
        if (completion.thrownByProcess == null && completion.callsWhenProcessReturned == 0) {
            // a producer that does not wait for the answer gets the time to complete the exchange, so nothing hangs
            assertThat(completion.called.await(20, TimeUnit.SECONDS))
                    .as("the exchange was completed within 20 seconds").isTrue();
        }
        return completion;
    }

    private String telegramUri() {
        return "telegram:bots?authorizationToken=mock-token&chatId=" + chatId;
    }

    private String closedPortUri() {
        return telegramUri() + "&baseUri=http://localhost:" + CLOSED_PORT.getPort();
    }

    private static OutgoingTextMessage textMessage() {
        OutgoingTextMessage message = new OutgoingTextMessage();
        message.setText("hello");
        return message;
    }

    private static final class Completion {
        private final Exchange exchange;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<Boolean> failedAtFirstCall = new AtomicReference<>();
        private final AtomicReference<Thread> callingThread = new AtomicReference<>();
        private final CountDownLatch called = new CountDownLatch(1);
        // written and read by the test thread only
        private RuntimeException thrownByProcess;
        private int callsWhenProcessReturned;

        private Completion(Exchange exchange) {
            this.exchange = exchange;
        }
    }

    /**
     * Answers sendVenue. It keeps the first answer back for a second, or until a second request arrives: a producer
     * that sends the second message before the first is answered shows up in the order of the events.
     */
    private static final class HoldingApi implements Processor {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch secondArrived = new CountDownLatch(1);

        @Override
        public void process(Exchange exchange) throws Exception {
            int request = requests.incrementAndGet();
            events.add("request " + request);
            if (request == 1) {
                if (secondArrived.await(1, TimeUnit.SECONDS)) {
                    events.add("request 2 arrived before answer 1");
                }
            } else {
                secondArrived.countDown();
            }
            events.add("answer " + request);
            byte[] bytes = TelegramTestUtil.stringResource("messages/send-venue.json").getBytes(StandardCharsets.UTF_8);
            exchange.getMessage().setBody(bytes);
            exchange.getMessage().setHeader("Content-Length", bytes.length);
            exchange.getMessage().setHeader("Content-Type", "application/json; charset=UTF-8");
        }
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("netty-http:http://localhost:" + port.getPort()
                             + "/botmock-token/sendVenue?httpMethodRestrict=POST")
                                .process(venueApi);

                        from("direct:telegram")
                                .to(telegramUri())
                                .to("mock:after");

                        from("direct:closed")
                                .to(closedPortUri())
                                .to("mock:after");

                        from("direct:handled")
                                .onException(Exception.class).handled(true).to("mock:failed").end()
                                .to(telegramUri())
                                .to("mock:after");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port.getPort())
                // Telegram answers 400, for example for a chat that does not exist
                .addErrorEndpoint("sendMessage", "POST", 400)
                // 200 with a body that is not JSON, for example the error page of a proxy
                .addEndpoint("sendLocation", "POST", String.class, "<html>Bad Gateway</html>");
    }
}
