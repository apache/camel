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
package org.apache.camel.component.openai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.openai.models.webhooks.UnwrapWebhookEvent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook consumer without an HTTP server: the recording factory hands back the processor that platform-http would
 * call, so a request can be built by hand and its answer read from the exchange.
 */
class OpenAIWebhookConsumerTest extends CamelTestSupport {

    private static final String SECRET
            = "whsec_" + Base64.getEncoder().encodeToString("a-webhook-secret".getBytes(StandardCharsets.UTF_8));

    private static final String RESPONSE_COMPLETED
            = "{\"id\":\"evt_abc\",\"created_at\":1758000000,\"type\":\"response.completed\","
              + "\"data\":{\"id\":\"resp_123\"},\"object\":\"event\"}";

    private RecordingRestConsumerFactory factory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        factory = RecordingRestConsumerFactory.bindTo(context);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("openai:webhook?webhookSecret=%s&webhookPath=/openai/events"
                      + "&httpServerComponent=recordingRestConsumerFactory", SECRET)
                        .to("mock:events");
            }
        };
    }

    @Test
    void registersOnePostRoute() {
        assertThat(factory.registrations()).singleElement()
                .satisfies(registration -> {
                    assertThat(registration.verb()).isEqualTo("POST");
                    assertThat(registration.path()).isEqualTo("/openai/events");
                });
    }

    @Test
    void signedEventReachesTheRoute() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(1);

        Exchange request = request(RESPONSE_COMPLETED, signedHeaders("evt_abc", RESPONSE_COMPLETED, now()));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);

        Exchange received = events.getExchanges().get(0);
        UnwrapWebhookEvent event = received.getMessage().getBody(UnwrapWebhookEvent.class);
        assertThat(event.isResponseCompleted()).isTrue();
        assertThat(event.asResponseCompleted().data().id()).isEqualTo("resp_123");
        assertThat(received.getMessage().getHeader(OpenAIConstants.WEBHOOK_EVENT_TYPE)).isEqualTo("response.completed");
        assertThat(received.getMessage().getHeader(OpenAIConstants.WEBHOOK_EVENT_ID)).isEqualTo("evt_abc");
        assertThat(received.getMessage().getHeader(OpenAIConstants.WEBHOOK_OBJECT_ID)).isEqualTo("resp_123");
        assertThat(received.getMessage().getHeader(OpenAIConstants.WEBHOOK_CREATED_AT)).isEqualTo(1758000000L);
    }

    @Test
    void theRouteReadsAStreamedBodyToo() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(1);

        Exchange request = request(RESPONSE_COMPLETED, signedHeaders("evt_abc", RESPONSE_COMPLETED, now()));
        request.getMessage().setBody(new ByteArrayInputStream(RESPONSE_COMPLETED.getBytes(StandardCharsets.UTF_8)));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);
    }

    @Test
    void anEventTypeTheSdkDoesNotKnowIsStillDescribed() throws Exception {
        String payload = "{\"id\":\"evt_new\",\"created_at\":1758000001,\"type\":\"some.future.event\","
                         + "\"data\":{\"id\":\"obj_1\"}}";
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(1);

        Exchange request = request(payload, signedHeaders("evt_new", payload, now()));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);
        assertThat(events.getExchanges().get(0).getMessage().getHeader(OpenAIConstants.WEBHOOK_EVENT_TYPE))
                .isEqualTo("some.future.event");
        assertThat(events.getExchanges().get(0).getMessage().getHeader(OpenAIConstants.WEBHOOK_OBJECT_ID))
                .isEqualTo("obj_1");
    }

    @Test
    void aWrongSignatureIsAnsweredWithoutRunningTheRoute() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        Exchange request = request(RESPONSE_COMPLETED, signedHeaders("evt_abc", "another payload", now()));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
    }

    @Test
    void anEventOlderThanTheToleranceIsRejected() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        Exchange request = request(RESPONSE_COMPLETED, signedHeaders("evt_abc", RESPONSE_COMPLETED, now() - 3600));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
        // the answer says nothing about why, the detail is logged
        assertThat(request.getMessage().getBody(String.class)).isEqualTo("Invalid webhook request");
    }

    @Test
    void aRequestWithoutTheSignatureHeadersIsRejected() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        Exchange request = request(RESPONSE_COMPLETED, List.of());
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
    }

    @Test
    void aSignedBodyThatIsNotAnEventIsRejected() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        String payload = "not an event";
        Exchange request = request(payload, signedHeaders("evt_abc", payload, now()));
        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
    }

    @Test
    void aBodyLargerThanTheLimitIsRejectedBeforeVerification() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        String payload = "{\"data\":\"" + "x".repeat(2048) + "\"}";
        Exchange request = request(payload, signedHeaders("evt_big", payload, now()));
        request.getMessage().setBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        webhookEndpoint().getConfiguration().setWebhookMaxPayloadSize(1024);
        try {
            factory.dispatch(request);

            events.assertIsSatisfied();
            assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(413);
        } finally {
            webhookEndpoint().getConfiguration().setWebhookMaxPayloadSize(1048576);
        }
    }

    @Test
    void aBodyLargerThanTheLimitIsRejectedWhenItArrivesAsText() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        String payload = "{\"data\":\"" + "x".repeat(2048) + "\"}";
        Exchange request = request(payload, signedHeaders("evt_big_text", payload, now()));
        webhookEndpoint().getConfiguration().setWebhookMaxPayloadSize(1024);
        try {
            factory.dispatch(request);

            events.assertIsSatisfied();
            assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(413);
        } finally {
            webhookEndpoint().getConfiguration().setWebhookMaxPayloadSize(1048576);
        }
    }

    @Test
    void aBodyThatBreaksWhileItIsReadIsAnsweredWith500() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(0);

        String payload = "{\"data\":\"x\"}";
        Exchange request = request(payload, signedHeaders("evt_broken", payload, now()));
        request.getMessage().setBody(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Connection reset by peer");
            }
        });

        factory.dispatch(request);

        events.assertIsSatisfied();
        assertThat(request.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(500);
    }

    private OpenAIEndpoint webhookEndpoint() {
        return context.getEndpoints().stream()
                .filter(OpenAIEndpoint.class::isInstance)
                .map(OpenAIEndpoint.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Exchange request(String payload, List<String[]> headers) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(payload);
        headers.forEach(header -> exchange.getMessage().setHeader(header[0], header[1]));
        return exchange;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private static List<String[]> signedHeaders(String id, String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring("whsec_".length())), "HmacSHA256"));
        String signed = id + "." + timestamp + "." + payload;
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        return List.of(
                new String[] { "webhook-id", id },
                new String[] { "webhook-timestamp", String.valueOf(timestamp) },
                new String[] { "webhook-signature", "v1," + signature });
    }
}
