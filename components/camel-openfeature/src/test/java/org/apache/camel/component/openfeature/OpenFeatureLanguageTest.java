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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.openfeature.OpenFeatureLanguage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenFeatureLanguageTest extends CamelTestSupport {

    private static final String FLAGS_RESOURCE = "classpath:openfeature/flags.json";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("OpenFeatureEvaluationWarning", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    @BeforeEach
    void captureWarnings() {
        appender.start();
        endpointLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        endpointLogger().setAdditive(true);
    }

    @AfterEach
    void removeAppender() {
        endpointLogger().removeAppender(appender);
        appender.stop();
    }

    private static Logger endpointLogger() {
        return (Logger) LogManager.getLogger(OpenFeatureEndpoint.class);
    }

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
                        .filter().language("openfeature", "targeted-boolean")
                            .to("mock:context-filtered")
                        .end();

                from("direct:language-then-producer")
                        .filter().language("openfeature", "enrichment-enabled")
                            .to("mock:lang-filtered")
                        .end()
                        .to("openfeature:flags/variant?flagKey=hazmat-compliance-v2&resultProperty=variant");

                from("direct:set-body-boolean")
                        .setBody().language("openfeature", "enrichment-enabled");

                from("direct:set-header-boolean")
                        .setHeader("flag").language("openfeature", "targeted-boolean");

                from("direct:set-property-string")
                        .setProperty("flag").language("openfeature", "hazmat-compliance-v2");

                from("direct:set-body-number")
                        .setBody().language("openfeature", "max-retries");

                from("direct:set-body-missing")
                        .setBody().language("openfeature", "no-such-flag");
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
    void testTargetedBooleanWithContextHeaders() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:context-filtered");
        filtered.expectedMessageCount(1);

        template.sendBodyAndHeaders("direct:with-context", "test-message",
                Map.of(OpenFeatureConstants.TARGETING_KEY, "user-123",
                        OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "ENTERPRISE")));

        filtered.assertIsSatisfied();
    }

    @Test
    void testTargetedBooleanBlocksForStandard() throws Exception {
        MockEndpoint filtered = getMockEndpoint("mock:context-filtered");
        filtered.expectedMessageCount(0);

        template.sendBodyAndHeaders("direct:with-context", "test-message",
                Map.of(OpenFeatureConstants.TARGETING_KEY, "user-456",
                        OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "STANDARD")));

        filtered.assertIsSatisfied();
    }

    @Test
    void testLanguageDoesNotLeakIntoProducer() {
        Exchange exchange = template.request("direct:language-then-producer", e -> {
            e.getMessage().setBody("test-message");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT,
                    Map.of("targetingKey", "order-123", "customer_tier", "ENTERPRISE"));
        });
        assertThat(exchange.getProperty("variant")).isEqualTo("v2");
        assertThat(exchange.getProperty(OpenFeatureConstants.EVALUATION_TYPE)).isNull();
        assertThat(exchange.getProperty(OpenFeatureConstants.FLAG_KEY)).isNull();
    }

    @Test
    void testCreatePredicateWithContext() throws Exception {
        Predicate flag = context.resolveLanguage("openfeature").createPredicate(
                "targeted-boolean",
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
    void testCreatePredicateBlocksForStandard() throws Exception {
        Predicate flag = context.resolveLanguage("openfeature").createPredicate(
                "targeted-boolean",
                new Object[] { null, "user-42", Map.of("customer_tier", "STANDARD") });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:predicate-context-standard")
                        .filter(flag)
                            .to("mock:predicate-filtered-standard")
                        .end();
            }
        });

        MockEndpoint filtered = getMockEndpoint("mock:predicate-filtered-standard");
        filtered.expectedMessageCount(0);

        template.sendBody("direct:predicate-context-standard", "test-message");

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
    void testSetBodyWithBooleanFlag() {
        Object body = template.requestBody("direct:set-body-boolean", "test-message");

        assertThat(body).isInstanceOf(Boolean.class).isEqualTo(Boolean.TRUE);
        assertThat(warnings).isEmpty();
    }

    @Test
    void testSetHeaderWithTargetedBooleanFlag() {
        Exchange enterprise = template.request("direct:set-header-boolean", e -> {
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "user-123");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "ENTERPRISE"));
        });
        Exchange standard = template.request("direct:set-header-boolean", e -> {
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "user-456");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "STANDARD"));
        });

        assertThat(enterprise.getMessage().getHeader("flag")).isInstanceOf(Boolean.class).isEqualTo(Boolean.TRUE);
        assertThat(standard.getMessage().getHeader("flag")).isInstanceOf(Boolean.class).isEqualTo(Boolean.FALSE);
        assertThat(warnings).isEmpty();
    }

    @Test
    void testSetPropertyWithStringFlag() {
        Exchange exchange = template.request("direct:set-property-string", e -> {
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "order-123");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT, Map.of("customer_tier", "ENTERPRISE"));
        });

        assertThat(exchange.getProperty("flag")).isEqualTo("v2");
        assertThat(warnings).isEmpty();
    }

    @Test
    void testSetBodyWithMissingFlagGivesDefaultValue() {
        Object body = template.requestBody("direct:set-body-missing", "test-message");

        assertThat(body).isEqualTo("false");
        assertThat(warnings).hasSize(1).first().asString().contains("no-such-flag", "FLAG_NOT_FOUND");
    }

    @Test
    void testSetBodyWithNumberFlagGivesDefaultValue() {
        Object body = template.requestBody("direct:set-body-number", "test-message");

        assertThat(body).isEqualTo("false");
        assertThat(warnings).hasSize(1).first().asString().contains("max-retries", "TYPE_MISMATCH");
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
