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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitrusVariableTest extends CamelTestSupport {

    @Test
    void shouldPropagateExchangeVariableToCitrusContext() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validateAll().body("{\"greeting\": \"Hello ${userName}!\"}");
        citrus.expectedMessageCount(1);

        template.send("direct:start", exchange -> {
            exchange.setVariable("userName", "World");
            exchange.getIn().setBody("{\"greeting\": \"Hello World!\"}");
        });

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldExtractVariableFromBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validateAll().body("{\"orderId\": \"@variable('extractedId')@\", \"status\": \"created\"}");
        citrus.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("{\"orderId\": \"ABC-123\", \"status\": \"created\"}");
        });

        citrus.assertIsSatisfied();
        assertThat(result.getVariable("extractedId")).isEqualTo("ABC-123");
    }

    @Test
    void shouldExtractVariableEvenWhenPropertyExists() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validateAll().body("{\"orderId\": \"@variable('orderId')@\", \"status\": \"ok\"}");
        citrus.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.setProperty("orderId", "old-value");
            exchange.getIn().setBody("{\"orderId\": \"new-value\", \"status\": \"ok\"}");
        });

        citrus.assertIsSatisfied();
        assertThat(result.getVariable("orderId")).isEqualTo("new-value");
        assertThat(result.getProperty("orderId")).isEqualTo("old-value");
    }

    @Test
    void shouldNotWriteBackPropagatedPropertiesAsVariables() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);

        citrus.validateAll().body("{\"status\": \"ok\"}");
        citrus.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.setProperty("myProp", "propValue");
            exchange.getIn().setBody("{\"status\": \"ok\"}");
        });

        citrus.assertIsSatisfied();
        assertThat(result.getProperty("myProp")).isEqualTo("propValue");
        assertThat(result.getVariables().containsKey("myProp")).isFalse();
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
