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

import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.http.NettyHttpComponent;
import org.apache.camel.component.netty.http.NettyHttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error fault with {@code status=} for camel-netty-http: its {@code NettyHttpOperationFailedException} for that
 * status, or its response when the endpoint has {@code throwExceptionOnFailure=false} or an {@code okStatusCodeRange}
 * with the status. The producer is not called: nothing listens on the port of the endpoints.
 */
class ChaosNettyHttpFaultTest extends ChaosTestSupport {

    private static final String INVENTORY = "netty-http:http://localhost:9/stock";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("nh", new NettyHttpComponent());
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:throwing").routeId("throwing").to(INVENTORY);
                // camel-netty-http under a name of its own
                from("direct:named").routeId("named").to("nh:http://localhost:9/stock");
                from("direct:quiet").routeId("quiet").to(INVENTORY + "?throwExceptionOnFailure=false");
                from("direct:accepting").routeId("accepting").to(INVENTORY + "?okStatusCodeRange=200-299,503");
                from("direct:handled").routeId("handled")
                        .onException(NettyHttpOperationFailedException.class).handled(true).to("mock:handled").end()
                        .to(INVENTORY)
                        .to("mock:after");
            }
        };
    }

    @Test
    void statusRaisesTheNettyHttpOperationFailedException() {
        String id = chaos().addRule("component=netty-http error status=503 header.Retry-After=5 body=\"no stock\"");

        Exchange out = template.send("direct:throwing", e -> {
            e.getMessage().setBody("order");
            e.getMessage().setHeader("order-id", "42");
        });

        assertThat(out.getException()).isInstanceOf(NettyHttpOperationFailedException.class);
        NettyHttpOperationFailedException failure = (NettyHttpOperationFailedException) out.getException();
        assertThat(failure.getStatusCode()).isEqualTo(503);
        assertThat(failure.getStatusText()).isEqualTo("Service Unavailable");
        assertThat(failure.getContentAsString()).isEqualTo("no stock");
        assertThat(failure.isRedirectError()).isFalse();
        // the address of the endpoint without its options, as the url of the request of camel-netty-http
        assertThat(failure.getUri()).isEqualTo("http://localhost:9/stock");
        // the content is the response, as camel-netty-http gives it
        assertThat(failure.getHttpContent()).isInstanceOf(FullHttpResponse.class);
        FullHttpResponse response = (FullHttpResponse) failure.getHttpContent();
        assertThat(response.status().code()).isEqualTo(503);
        assertThat(response.status().reasonPhrase()).isEqualTo("Service Unavailable");
        assertThat(response.headers().get("Retry-After")).isEqualTo("5");
        // as camel-netty-http, the message is the response (chaos keeps the request headers)
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT)).isEqualTo("Service Unavailable");
        assertThat(out.getMessage().getHeader("Retry-After")).isEqualTo("5");
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("no stock");
        assertThat(out.getMessage().getHeader("order-id")).isEqualTo("42");
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void withoutBodyTheContentIsEmpty() {
        chaos().addRule("endpoint=netty-http:http://localhost:9/* error status=500");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        NettyHttpOperationFailedException failure = (NettyHttpOperationFailedException) out.getException();
        assertThat(failure.getStatusCode()).isEqualTo(500);
        assertThat(failure.getStatusText()).isEqualTo("Internal Server Error");
        assertThat(failure.getContentAsString()).isEmpty();
        assertThat(out.getMessage().getBody(String.class)).isEmpty();
    }

    @Test
    void locationHeaderIsTheRedirectLocation() {
        chaos().addRule("component=netty-http error status=302 header.Location=http://elsewhere/stock");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        NettyHttpOperationFailedException failure = (NettyHttpOperationFailedException) out.getException();
        assertThat(failure.isRedirectError()).isTrue();
        assertThat(failure.hasRedirectLocation()).isTrue();
        assertThat(failure.getRedirectLocation()).isEqualTo("http://elsewhere/stock");
    }

    @Test
    void aLessCommonStatusHasNoReasonPhrase() {
        chaos().addRule("component=netty-http error status=418");

        Exchange out = template.send("direct:throwing", e -> {
            e.getMessage().setBody("order");
            // a text from an earlier response does not stay
            e.getMessage().setHeader(Exchange.HTTP_RESPONSE_TEXT, "OK");
        });

        NettyHttpOperationFailedException failure = (NettyHttpOperationFailedException) out.getException();
        assertThat(failure.getStatusCode()).isEqualTo(418);
        assertThat(failure.getStatusText()).isEmpty();
        assertThat(out.getMessage().getHeaders()).doesNotContainKey(Exchange.HTTP_RESPONSE_TEXT);
    }

    @Test
    void withoutThrowExceptionOnFailureTheResponseIsReturned() {
        String id = chaos().addRule("component=netty-http error status=503 header.Retry-After=5");

        Exchange out = template.send("direct:quiet", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT)).isEqualTo("Service Unavailable");
        assertThat(out.getMessage().getHeader("Retry-After")).isEqualTo("5");
        // without body= the body is empty
        assertThat(out.getMessage().getBody(String.class)).isEmpty();
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void aStatusInTheOkRangeOfTheEndpointIsAResponse() {
        String id = chaos().addRule("component=netty-http error status=503 body=busy");

        Exchange accepting = template.send("direct:accepting", e -> e.getMessage().setBody("order"));
        Exchange throwing = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(accepting.getException()).isNull();
        assertThat(accepting.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(503);
        assertThat(accepting.getMessage().getBody(String.class)).isEqualTo("busy");
        assertThat(throwing.getException()).isInstanceOf(NettyHttpOperationFailedException.class);
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void nettyHttpEndpointsAreMatchedByTheirComponent() {
        // camel-netty-http gives its endpoints the URI of their address (http://localhost:9/stock): chaos matches them
        // as netty-http:http://localhost:9/stock, so an http rule does not take them for camel-http endpoints
        String http = chaos().addRule("component=http error status=503");
        String httpUri = chaos().addRule("endpoint=http://localhost:9/* error status=503");
        String netty = chaos().addRule("endpoint=netty-http:http://localhost:9/* error status=504");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isInstanceOf(NettyHttpOperationFailedException.class);
        assertThat(((NettyHttpOperationFailedException) out.getException()).getStatusCode()).isEqualTo(504);
        assertThat(ruleReport(http).getLong("matches")).isZero();
        assertThat(ruleReport(httpUri).getLong("matches")).isZero();
        assertThat(fired(netty, "send")).isEqualTo(1);
    }

    @Test
    void aComponentUnderANameOfItsOwnIsMatchedByItsDefaultName() {
        // an endpoint is matched with the default name of its component in front, not the name it has in the context
        String named = chaos().addRule("component=nh error message=named");
        String netty = chaos().addRule("component=netty-http error status=504");

        Exchange out = template.send("direct:named", e -> e.getMessage().setBody("order"));

        assertThat(((NettyHttpOperationFailedException) out.getException()).getStatusCode()).isEqualTo(504);
        assertThat(ruleReport(named).getLong("matches")).isZero();
        assertThat(fired(netty, "send")).isEqualTo(1);
    }

    @Test
    void aLocationIsARedirectLocationOnlyForARedirect() {
        // as camel-netty-http (and camel-http): the location of the failure is the one of a 3xx response only
        chaos().addRule("component=netty-http error status=503 header.Location=http://elsewhere/stock");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        NettyHttpOperationFailedException failure = (NettyHttpOperationFailedException) out.getException();
        assertThat(failure.getRedirectLocation()).isNull();
        assertThat(((FullHttpResponse) failure.getHttpContent()).headers().get("Location")).isEqualTo("http://elsewhere/stock");
    }

    @Test
    void aHeaderValueThatNettyRefusesFailsTheSendWithItsReason() {
        // Netty validates the header values of a response; the rule's value with a leading space is refused
        chaos().addRule("component=netty-http error status=503 header.X-Note=\" busy\"");

        Exchange out = template.send("direct:throwing", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        assertThat(out.getException().getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onExceptionOfTheRouteHandlesTheFailure() throws Exception {
        MockEndpoint handled = getMockEndpoint("mock:handled");
        handled.expectedMessageCount(1);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(0);
        chaos().addRule("component=netty-http error status=503");

        Exchange out = template.send("direct:handled", e -> e.getMessage().setBody("order"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        NettyHttpOperationFailedException caught = handled.getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, NettyHttpOperationFailedException.class);
        assertThat(caught.getStatusCode()).isEqualTo(503);
    }
}
