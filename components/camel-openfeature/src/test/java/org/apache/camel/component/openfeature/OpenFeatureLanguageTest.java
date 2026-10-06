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
package org.apache.camel.component.openfeature;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.openfeature.OpenFeatureLanguage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenFeatureLanguageTest extends CamelTestSupport {

    private static final String FLAGS_RESOURCE = "classpath:openfeature/flags.json";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        OpenFeatureComponent component = new OpenFeatureComponent();
        component.getConfiguration().setFlagsResource(FLAGS_RESOURCE);
        camelContext.addComponent("openfeature", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:filter-enabled")
                        .filter().language("openfeature", "enrichment-enabled")
                            .to("mock:filtered")
                        .end()
                        .to("mock:after-filter");

                from("direct:filter-disabled")
                        .filter().language("openfeature", "disabled-flag")
                            .to("mock:filtered-disabled")
                        .end()
                        .to("mock:after-filter-disabled");

                from("direct:choice")
                        .choice()
                            .when().language("openfeature", "enrichment-enabled")
                                .to("mock:choice-enabled")
                            .otherwise()
                                .to("mock:choice-disabled")
                        .end();

                from("direct:with-context")
                        .filter().language("openfeature", "enrichment-enabled")
                            .to("mock:context-filtered")
                        .end();
            }
        };
    }

    @Test
    void testFilterPassesWhenFlagEnabled() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:filtered");
        filtered.expectedMessageCount(1);
        MockEndpoint afterFilter = getMockEndpoint("mock:after-filter");
        afterFilter.expectedMessageCount(1);

        template.sendBody("direct:filter-enabled", "test-message");

        filtered.assertIsSatisfied();
        afterFilter.assertIsSatisfied();
    }

    @Test
    void testFilterBlocksWhenFlagDisabled() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:filtered-disabled");
        filtered.expectedMessageCount(0);
        MockEndpoint afterFilter = getMockEndpoint("mock:after-filter-disabled");
        afterFilter.expectedMessageCount(1);

        template.sendBody("direct:filter-disabled", "test-message");

        filtered.assertIsSatisfied();
        afterFilter.assertIsSatisfied();
    }

    @Test
    void testChoiceRoutesToCorrectBranch() throws Exception {
        MockEndpoint enabled = getMockEndpoint("mock:choice-enabled");
        enabled.expectedMessageCount(1);
        MockEndpoint disabled = getMockEndpoint("mock:choice-disabled");
        disabled.expectedMessageCount(0);

        template.sendBody("direct:choice", "test-message");

        enabled.assertIsSatisfied();
        disabled.assertIsSatisfied();
    }

    @Test
    void testEvaluationContextViaHeaders() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:context-filtered");
        filtered.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:with-context", "test-message",
                Map.of(OpenFeatureConstants.TARGETING_KEY, "user-123",
                        OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "ENTERPRISE")));

        filtered.assertIsSatisfied();
    }

    @Test
    void testCreatePredicateWithContext() throws Exception {
        Predicate flag = context.resolveLanguage("openfeature").createPredicate(
                "enrichment-enabled",
                new Object[] { null, "user-42", Map.of("customer_tier", "ENTERPRISE") });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:predicate-context")
                        .filter(flag)
                            .to("mock:predicate-filtered")
                        .end();
            }
        });

        MockEndpoint filtered = getMockEndpoint("mock:predicate-filtered");
        filtered.expectedMessageCount(1);

        template.sendBody("direct:predicate-context", "test-message");

        filtered.assertIsSatisfied();
    }

    @Test
    void testCreateExpressionWithContext() {
        Expression expr = context.resolveLanguage("openfeature").createExpression(
                "hazmat-compliance-v2",
                new Object[] { null, "order-123", Map.of("customer_tier", "ENTERPRISE") });

        Exchange exchange = TestSupport.createExchangeWithBody(context, "test-message");
        Object result = expr.evaluate(exchange, Object.class);
        assertThat(result).isEqualTo("v2");
    }

    @Test
    void testCreateExpressionBooleanFlag() {
        Expression expr = context.resolveLanguage("openfeature").createExpression("enrichment-enabled");

        Exchange exchange = TestSupport.createExchangeWithBody(context, "test-message");
        Boolean result = expr.evaluate(exchange, Boolean.class);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testValidatesBlankFlagKey() {
        OpenFeatureLanguage language = new OpenFeatureLanguage();
        assertThatThrownBy(() -> language.createPredicate(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> language.createPredicate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
