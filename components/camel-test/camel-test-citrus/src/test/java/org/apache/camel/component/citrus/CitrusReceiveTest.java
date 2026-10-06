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
package org.apache.camel.component.citrus;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitrusReceiveTest extends CamelTestSupport {

    @Test
    void shouldValidateAndRespond() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"greet\"}");
        mock.then().allMessages().respond()
                .body("{\"greeting\": \"Hello!\"}");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"greet\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"greeting\": \"Hello!\"}");
    }

    @Test
    void shouldRespondWithHeaders() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"greet\"}");
        mock.then().allMessages().respond()
                .body("{\"greeting\": \"Hello!\"}")
                .header("Content-Type", "application/json")
                .header("X-Status", "ok");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"greet\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"greeting\": \"Hello!\"}");
        assertThat(result.getIn().getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(result.getIn().getHeader("X-Status")).isEqualTo("ok");
    }

    @Test
    void shouldRespondWithCitrusVariables() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"greet\", \"name\": \"@ignore@\"}");
        mock.then().allMessages().respond()
                .body("{\"greeting\": \"Hello ${userName}!\"}");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.setProperty("userName", "World");
            exchange.getIn().setBody("{\"action\": \"greet\", \"name\": \"World\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"greeting\": \"Hello World!\"}");
    }

    @Test
    void shouldRespondWithVariablesInHeaders() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"check\"}");
        mock.then().allMessages().respond()
                .body("{\"result\": \"ok\"}")
                .header("X-Correlation-Id", "${correlationId}");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.setProperty("correlationId", "abc-123");
            exchange.getIn().setBody("{\"action\": \"check\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"result\": \"ok\"}");
        assertThat(result.getIn().getHeader("X-Correlation-Id")).isEqualTo("abc-123");
    }

    @Test
    void shouldHandleSequentialReceiveAndRespond() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().message(0).receive().body("{\"action\": \"create\"}");
        mock.then().message(0).respond().body("{\"id\": \"1\", \"status\": \"created\"}");

        mock.when().message(1).receive().body("{\"action\": \"update\"}");
        mock.then().message(1).respond().body("{\"id\": \"1\", \"status\": \"updated\"}");

        mock.expectedMessageCount(2);

        Exchange result1 = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"create\"}");
        });

        Exchange result2 = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"update\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result1.getIn().getBody(String.class)).isEqualTo("{\"id\": \"1\", \"status\": \"created\"}");
        assertThat(result2.getIn().getBody(String.class)).isEqualTo("{\"id\": \"1\", \"status\": \"updated\"}");
    }

    @Test
    void shouldFallBackToAllMessagesResponse() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().message(0).receive().body("{\"action\": \"first\"}");
        mock.then().message(0).respond().body("{\"result\": \"special\"}");

        mock.when().allMessages().receive().body("{\"action\": \"@ignore@\"}");
        mock.then().allMessages().respond().body("{\"result\": \"default\"}");

        mock.expectedMessageCount(3);

        Exchange result1 = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"first\"}");
        });
        Exchange result2 = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"second\"}");
        });
        Exchange result3 = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"third\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result1.getIn().getBody(String.class)).isEqualTo("{\"result\": \"special\"}");
        assertThat(result2.getIn().getBody(String.class)).isEqualTo("{\"result\": \"default\"}");
        assertThat(result3.getIn().getBody(String.class)).isEqualTo("{\"result\": \"default\"}");
    }

    @Test
    void shouldUseSendAliasForRespond() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive().body("{\"ping\": true}");
        mock.then().allMessages().send().body("{\"pong\": true}");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"ping\": true}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"pong\": true}");
    }

    @Test
    void shouldRespondWithoutValidation() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/responder", CitrusEndpoint.class);

        mock.then().allMessages().respond()
                .body("OK")
                .header("status", "200");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:responder", exchange -> {
            exchange.getIn().setBody("any request");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("OK");
        assertThat(result.getIn().getHeader("status")).isEqualTo("200");
    }

    @Test
    void shouldRespondWithPayloadBuilder() throws Exception {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"greet\"}");
        mock.then().allMessages().respond()
                .body(testContext -> "{\"greeting\": \"Hello from builder!\"}");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"greet\"}");
        });

        mock.assertIsSatisfied();
        assertThat(result.getIn().getBody(String.class)).isEqualTo("{\"greeting\": \"Hello from builder!\"}");
    }

    @Test
    void shouldFailOnValidationMismatch() {
        CitrusEndpoint mock = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);

        mock.when().allMessages().receive()
                .body("{\"action\": \"greet\"}");
        mock.then().allMessages().respond()
                .body("{\"greeting\": \"Hello!\"}");

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"action\": \"wrong\"}");
        });

        assertThat(result.getException()).isNotNull();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:receive/service?messageType=json");
                from("direct:responder").to("citrus:receive/responder");
            }
        };
    }
}
