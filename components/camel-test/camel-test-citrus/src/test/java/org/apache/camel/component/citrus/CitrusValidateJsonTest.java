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
import org.citrusframework.validation.DefaultValidations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitrusValidateJsonTest extends CamelTestSupport {

    @Test
    void shouldValidateJsonBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body("""
                {
                  "greeting": "Hello World!"
                }
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                {
                  "greeting": "Hello World!"
                }
                """);

        citrus.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(1);
    }

    @Test
    void shouldFailOnJsonBodyMismatch() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body("""
                {
                  "greeting": "Hello World!"
                }
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                {
                  "greeting": "Goodbye!"
                }
                """);

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(ValidationException.class);
    }

    @Test
    void shouldValidateJsonPathExpressions() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().validate(new DefaultValidations().jsonPath()
                .expression("$.user.name", "John")
                .expression("$.user.age", "30"));
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                {
                  "user": {
                    "name": "John",
                    "age": 30
                  }
                }
                """);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldAutoDetectJsonMessageType() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate", CitrusEndpoint.class);
        citrus.validateAll().body("""
                {
                  "greeting": "Hello World!"
                }
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:auto", """
                {
                  "greeting": "Hello World!"
                }
                """);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldValidateHeaders() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll()
                .body("""
                        {
                          "result": "ok"
                        }
                        """)
                .header("operation", "test");
        citrus.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start",
                "{\"result\": \"ok\"}", "operation", "test");

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldPropagateExchangePropertiesAsVariables() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body("""
                {
                  "message": "Hello ${userName}!"
                }
                """);
        citrus.expectedMessageCount(1);

        template.send("direct:start", exchange -> {
            exchange.setProperty("userName", "World");
            exchange.getIn().setBody("""
                    {
                      "message": "Hello World!"
                    }
                    """);
        });

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldValidateWithPayloadBuilder() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body(testContext -> """
                {
                  "greeting": "Hello World!"
                }
                """);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:start", """
                {
                  "greeting": "Hello World!"
                }
                """);

        citrus.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("citrus:validate?messageType=json");
                from("direct:auto").to("citrus:validate");
            }
        };
    }
}
