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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.citrusframework.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitrusSequentialValidationTest extends CamelTestSupport {

    @Test
    void shouldValidateSequentialExchanges() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate().body("""
                {"event": "created"}
                """);
        citrus.validate().body("""
                {"event": "updated"}
                """);
        citrus.validate().body("""
                {"event": "deleted"}
                """);
        citrus.expectedMessageCount(3);

        template.sendBody("direct:start", "{\"event\": \"created\"}");
        template.sendBody("direct:start", "{\"event\": \"updated\"}");
        template.sendBody("direct:start", "{\"event\": \"deleted\"}");

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(3);
    }

    @Test
    void shouldValidateSequentialExchangesWithHeaders() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate()
                .body("{\"status\": \"pending\"}")
                .header("action", "create");
        citrus.validate()
                .body("{\"status\": \"done\"}")
                .header("action", "complete");
        citrus.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "{\"status\": \"pending\"}", "action", "create");
        template.sendBodyAndHeader("direct:start", "{\"status\": \"done\"}", "action", "complete");

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldPassThroughExchangeWithoutMatchingSpec() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate(1).body("{\"event\": \"first\"}");
        citrus.expectedMessageCount(2);

        template.sendBody("direct:start", "{\"event\": \"first\"}");
        template.sendBody("direct:start", "{\"event\": \"second\"}");

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(2);
    }

    @Test
    void shouldFallBackToAllMessagesSpec() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate(1).body("{\"event\": \"first\"}");
        citrus.validateAll().body("{\"event\": \"default\"}");
        citrus.expectedMessageCount(3);

        template.sendBody("direct:start", "{\"event\": \"first\"}");
        template.sendBody("direct:start", "{\"event\": \"default\"}");
        template.sendBody("direct:start", "{\"event\": \"default\"}");

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(3);
    }

    @Test
    void shouldValidateAllMessagesWithSameSpec() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validateAll()
                .body("{\"status\": \"ok\"}")
                .header("type", "health");
        citrus.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:start", "{\"status\": \"ok\"}", "type", "health");
        template.sendBodyAndHeader("direct:start", "{\"status\": \"ok\"}", "type", "health");
        template.sendBodyAndHeader("direct:start", "{\"status\": \"ok\"}", "type", "health");

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldFailSequentialValidationOnMismatch() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate(1).body("{\"event\": \"created\"}");
        citrus.validate(2).body("{\"event\": \"updated\"}");
        citrus.expectedMessageCount(2);

        template.sendBody("direct:start", "{\"event\": \"created\"}");
        template.sendBody("direct:start", "{\"event\": \"wrong\"}");

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(ValidationException.class);
    }

    @Test
    void shouldFailWhenImpliedMinimumCountNotMet() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validate(1).body("{\"event\": \"first\"}");
        citrus.validate(2).body("{\"event\": \"second\"}");
        citrus.validate(3).body("{\"event\": \"third\"}");

        template.sendBody("direct:start", "{\"event\": \"first\"}");

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(AssertionError.class);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:validate/test?messageType=json");
            }
        };
    }
}
