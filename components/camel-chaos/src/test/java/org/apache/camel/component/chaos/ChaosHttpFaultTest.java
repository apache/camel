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
package org.apache.camel.component.chaos;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error fault with {@code status=}: the failure of the HTTP component for that status, or its response when the
 * endpoint has {@code throwExceptionOnFailure=false}. The producer is not called.
 */
class ChaosHttpFaultTest extends ChaosTestSupport {

    @BindToRegistry("https")
    private final TestHttpComponent https = new TestHttpComponent();

    // as camel-undertow, its endpoints have the URI of their address (https://inventory/stock)
    @BindToRegistry("undertow")
    private final TestHttpComponent undertow = new TestHttpComponent("undertow");

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:throwing").routeId("throwing").to("https://inventory/stock");
                from("direct:quiet").routeId("quiet").to("https://inventory/stock?throwExceptionOnFailure=false");
                from("direct:other").routeId("other").to("mock:inventory");
                from("direct:undertow").routeId("undertow").to("undertow:https://inventory/stock");
                from("direct:undertowQuiet").routeId("undertowQuiet")
                        .to("undertow:https://inventory/stock?throwExceptionOnFailure=false");
                from("direct:withOptions").routeId("withOptions")
                        .to("https://inventory/stock?okStatusCodeRange=200-299&throwExceptionOnFailure=true");
                from("direct:accepting").routeId("accepting").to("https://inventory/stock?okStatusCodeRange=200-299,503");
            }
        };
    }

    @Test
    void statusRaisesTheHttpOperationFailedException() {
        String id = chaos().addRule("endpoint=https:inventory* error status=503 header.Retry-After=5 body=\"no stock\"");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isInstanceOf(HttpOperationFailedException.class);
        HttpOperationFailedException failure = (HttpOperationFailedException) out.getException();
        assertThat(failure.getStatusCode()).isEqualTo(503);
        assertThat(failure.getStatusText()).isEqualTo("Service Unavailable");
        // as camel-http, the status is on the message too
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT)).isEqualTo("Service Unavailable");
        assertThat(failure.getResponseHeaders()).containsExactly(Map.entry("Retry-After", "5"));
        assertThat(failure.getResponseBody()).isEqualTo("no stock");
        assertThat(failure.getUri()).startsWith("https://inventory/stock");
        assertThat(https.sends.get()).isZero();
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void theUriOfTheFailureHasNoOptionsOfTheEndpoint() {
        chaos().addRule("endpoint=https:inventory* error status=503");

        Exchange out = template.send("direct:withOptions", e -> e.getMessage().setBody("order"));

        assertThat(((HttpOperationFailedException) out.getException()).getUri()).isEqualTo("https://inventory/stock");
    }

    @Test
    void locationHeaderIsTheRedirectLocation() {
        chaos().addRule("endpoint=https:inventory* error status=302 header.Location=https://elsewhere");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        HttpOperationFailedException failure = (HttpOperationFailedException) out.getException();
        assertThat(failure.isRedirectError()).isTrue();
        assertThat(failure.getRedirectLocation()).isEqualTo("https://elsewhere");
    }

    @Test
    void withoutThrowExceptionOnFailureTheResponseIsReturned() {
        String id = chaos().addRule("endpoint=https:inventory* error status=503 header.Retry-After=5");

        Exchange out = template.send("direct:quiet", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT)).isEqualTo("Service Unavailable");
        assertThat(out.getMessage().getHeader("Retry-After")).isEqualTo("5");
        // without body= the body is empty
        assertThat(out.getMessage().getBody(String.class)).isEmpty();
        assertThat(https.sends.get()).isZero();
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void aStatusInTheOkRangeOfTheEndpointIsAResponse() {
        String id = chaos().addRule("endpoint=https:inventory* error status=503 body=busy");

        Exchange accepting = template.send("direct:accepting", e -> e.getMessage().setBody("order"));
        Exchange throwing = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(accepting.getException()).isNull();
        assertThat(accepting.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(accepting.getMessage().getBody(String.class)).isEqualTo("busy");
        assertThat(throwing.getException()).isInstanceOf(HttpOperationFailedException.class);
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void undertowFailsWithTheResponseOnTheMessageButNoReasonPhrase() {
        chaos().addRule("component=undertow error status=503 header.Retry-After=5 body=\"no stock\"");

        Exchange out = template.send("direct:undertow", e -> {
            e.getMessage().setBody("order");
            e.getMessage().setHeader(Exchange.HTTP_RESPONSE_TEXT, "OK");
        });

        assertThat(out.getException()).isInstanceOf(HttpOperationFailedException.class);
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(out.getMessage().getHeader("Retry-After")).isEqualTo("5");
        assertThat(out.getMessage().getBody()).isEqualTo("no stock");
        assertThat(out.getMessage().getHeaders()).doesNotContainKey(Exchange.HTTP_RESPONSE_TEXT);
        assertThat(undertow.sends.get()).isZero();

        // the response without throwExceptionOnFailure has no reason phrase either
        Exchange quiet = template.send("direct:undertowQuiet", e -> e.getMessage().setBody("order"));
        assertThat(quiet.getException()).isNull();
        assertThat(quiet.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(quiet.getMessage().getHeaders()).doesNotContainKey(Exchange.HTTP_RESPONSE_TEXT);
        assertThat(quiet.getMessage().getBody()).isEqualTo("no stock");
    }

    @Test
    void aLessCommonStatusHasNoReasonPhrase() {
        chaos().addRule("endpoint=https:inventory* error status=418");

        Exchange out = template.send("direct:quiet", e -> {
            e.getMessage().setBody("order");
            // a text from an earlier response does not stay
            e.getMessage().setHeader(Exchange.HTTP_RESPONSE_TEXT, "OK");
        });
        Exchange failed = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(418);
        assertThat(out.getMessage().getHeaders()).doesNotContainKey(Exchange.HTTP_RESPONSE_TEXT);
        assertThat(((HttpOperationFailedException) failed.getException()).getStatusText()).isEmpty();
    }

    @Test
    void okStatusRanges() {
        assertThat(ChaosFaults.isOkStatus(503, "200-299")).isFalse();
        assertThat(ChaosFaults.isOkStatus(503, "200-299,503")).isTrue();
        assertThat(ChaosFaults.isOkStatus(503, " 500 - 599 ")).isTrue();
        assertThat(ChaosFaults.isOkStatus(503, "x-y,abc")).isFalse();
        assertThat(ChaosFaults.isOkStatus(503, null)).isFalse();
    }

    @Test
    void anOpenPatternSkipsEndpointsThatAreNotHttp() {
        String id = chaos().addRule("endpoint=*inventory* error status=503");

        Exchange other = template.send("direct:other", e -> e.getMessage().setBody("order"));
        Exchange http = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(other.getException()).isNull();
        assertThat(http.getException()).isInstanceOf(HttpOperationFailedException.class);
        assertThat(ruleReport(id).getLong("skipped")).isEqualTo(1);
        assertThat(fired(id, "send")).isEqualTo(1);
    }
}
