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

class CitrusValidateYamlTest extends CamelTestSupport {

    @Test
    void shouldValidateYamlBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=yaml", CitrusEndpoint.class);
        citrus.allMessages().body("""
                greeting: Hello World!
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                greeting: Hello World!
                """);

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCount()).isEqualTo(1);
    }

    @Test
    void shouldAutoDetectYamlMessageType() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate", CitrusEndpoint.class);
        citrus.allMessages().body("""
                greeting: Hello World!
                items:
                  - name: first
                  - name: second
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:auto", """
                greeting: Hello World!
                items:
                  - name: first
                  - name: second
                """);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldPropagateExchangePropertiesAsVariables() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=yaml", CitrusEndpoint.class);
        citrus.allMessages().body("""
                greeting: Hello ${userName}!
                """);
        citrus.expectedMessageCount(1);

        template.send("direct:start", exchange -> {
            exchange.setProperty("userName", "World");
            exchange.getIn().setBody("""
                    greeting: Hello World!
                    """);
        });

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldFailOnYamlBodyMismatch() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=yaml", CitrusEndpoint.class);
        citrus.allMessages().body("""
                greeting: Hello World!
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                greeting: Goodbye!
                """);

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(ValidationException.class);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:validate?messageType=yaml");
                from("direct:auto").to("citrus:validate");
            }
        };
    }
}
