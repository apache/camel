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

import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.Value;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFeatureProducerTest extends CamelTestSupport {

    private static final String FLAGS_RESOURCE = "classpath:openfeature/flags.json";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:boolean-flag")
                        .to("openfeature:test?flagKey=enrichment-enabled&flagsResource=" + FLAGS_RESOURCE);

                from("direct:disabled-flag")
                        .to("openfeature:test-disabled?flagKey=disabled-flag&flagsResource=" + FLAGS_RESOURCE);

                from("direct:string-flag")
                        .to("openfeature:test-string?flagKey=new-routing-algorithm&defaultValue=unknown&flagsResource="
                            + FLAGS_RESOURCE);

                from("direct:result-property")
                        .to("openfeature:test-prop?flagKey=enrichment-enabled&resultProperty=flagResult&flagsResource="
                            + FLAGS_RESOURCE);

                from("direct:targeted-flag")
                        .to("openfeature:test-targeted?flagKey=hazmat-compliance-v2&defaultValue=v1&flagsResource="
                            + FLAGS_RESOURCE
                            + "&contextFromBody=true");

                from("direct:variant-type")
                        .to("openfeature:test-variant?flagKey=new-routing-algorithm&evaluationType=variant&flagsResource="
                            + FLAGS_RESOURCE);

                from("direct:boolean-type-explicit")
                        .to("openfeature:test-bool-type?flagKey=enrichment-enabled&evaluationType=boolean&flagsResource="
                            + FLAGS_RESOURCE);

                from("direct:targeted-boolean")
                        .to("openfeature:test-targeted-bool?flagKey=targeted-boolean&flagsResource="
                            + FLAGS_RESOURCE
                            + "&contextFromBody=true");

                from("direct:targeted-boolean-header")
                        .to("openfeature:test-targeted-bool-hdr?flagKey=targeted-boolean&flagsResource="
                            + FLAGS_RESOURCE);
            }
        };
    }

    @Test
    void testBooleanFlagEvaluationTrue() {
        Object result = template.requestBody("direct:boolean-flag", "ignored");
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testDisabledFlagReturnsDefault() {
        Object result = template.requestBody("direct:disabled-flag", "ignored");
        assertThat(result).isEqualTo(false);
    }

    @Test
    void testStringFlagEvaluation() {
        Object result = template.requestBody("direct:string-flag", "ignored");
        assertThat(result).isEqualTo("content-based-router");
    }

    @Test
    void testResultPropertyPreservesBody() {
        Exchange exchange = template.request("direct:result-property", e -> e.getMessage().setBody("original"));
        assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("original");
        assertThat(exchange.getProperty("flagResult")).isEqualTo(true);
    }

    @Test
    void testEvaluationContextTargetedEnterprise() {
        Object result = template.requestBody("direct:targeted-flag",
                Map.of("targetingKey", "order-123", "customer_tier", "ENTERPRISE"));
        assertThat(result).isEqualTo("v2");
    }

    @Test
    void testEvaluationContextTargetedVip() {
        Object result = template.requestBody("direct:targeted-flag",
                Map.of("targetingKey", "order-456", "customer_tier", "VIP"));
        assertThat(result).isEqualTo("v2");
    }

    @Test
    void testEvaluationContextTargetedStandard() {
        Object result = template.requestBody("direct:targeted-flag",
                Map.of("targetingKey", "order-789", "customer_tier", "STANDARD"));
        assertThat(result).isEqualTo("v1");
    }

    @Test
    void testFlagKeyHeaderOverride() {
        Object result = template.requestBodyAndHeader(
                "direct:boolean-flag", "ignored",
                OpenFeatureConstants.FLAG_KEY, "disabled-flag");
        assertThat(result).isEqualTo(false);
    }

    @Test
    void testTargetingKeyHeader() {
        Exchange exchange = template.request("direct:targeted-flag", e -> {
            e.getMessage().setBody(Map.of("customer_tier", "ENTERPRISE"));
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "order-header-123");
        });
        assertThat(exchange.getMessage().getBody()).isEqualTo("v2");
    }

    @Test
    void testTargetingKeyProperty() {
        Exchange exchange = template.request("direct:targeted-flag", e -> {
            e.getMessage().setBody(Map.of("customer_tier", "VIP"));
            e.setProperty(OpenFeatureConstants.TARGETING_KEY, "order-prop-456");
        });
        assertThat(exchange.getMessage().getBody()).isEqualTo("v2");
    }

    @Test
    void testContextHeader() {
        Exchange exchange = template.request("direct:targeted-flag", e -> {
            e.getMessage().setBody("ignored");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT,
                    Map.of("targetingKey", "order-ctx-789", "customer_tier", "ENTERPRISE"));
        });
        assertThat(exchange.getMessage().getBody()).isEqualTo("v2");
    }

    @Test
    void testContextHeaderOverridesBody() {
        Exchange exchange = template.request("direct:targeted-flag", e -> {
            e.getMessage().setBody(Map.of("targetingKey", "order-body", "customer_tier", "STANDARD"));
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_CONTEXT,
                    Map.of("customer_tier", "ENTERPRISE"));
        });
        assertThat(exchange.getMessage().getBody()).isEqualTo("v2");
    }

    @Test
    void testVariantEvaluationTypeWithDefaultDefaultValue() {
        Object result = template.requestBody("direct:variant-type", "ignored");
        assertThat(result).isEqualTo("content-based-router");
    }

    @Test
    void testExplicitBooleanEvaluationType() {
        Object result = template.requestBody("direct:boolean-type-explicit", "ignored");
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testInlineFlags() throws Exception {
        String inlineFlags = """
                {"flags":{"inline-flag":{"state":"ENABLED","variants":{"on":true,"off":false},"defaultVariant":"on"}}}""";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inline-flag")
                        .to("openfeature:test-inline?flagKey=inline-flag&flags=" + inlineFlags);
            }
        });
        Object result = template.requestBody("direct:inline-flag", "ignored");
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testBooleanFlagWithTargetingKeyReturnsBooleanNotString() {
        Exchange exchange = template.request("direct:targeted-boolean", e -> {
            e.getMessage().setBody(Map.of("customer_tier", "ENTERPRISE"));
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "user-123");
        });
        Object result = exchange.getMessage().getBody();
        assertThat(result).isInstanceOf(Boolean.class);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testBooleanFlagWithTargetingKeyDefaultVariant() {
        Exchange exchange = template.request("direct:targeted-boolean", e -> {
            e.getMessage().setBody(Map.of("customer_tier", "STANDARD"));
            e.getMessage().setHeader(OpenFeatureConstants.TARGETING_KEY, "user-456");
        });
        Object result = exchange.getMessage().getBody();
        assertThat(result).isInstanceOf(Boolean.class);
        assertThat(result).isEqualTo(false);
    }

    @Test
    void testBooleanFlagWithMapBodyReturnsBoolean() {
        Object result = template.requestBody("direct:targeted-boolean",
                Map.of("targetingKey", "user-789", "customer_tier", "VIP"));
        assertThat(result).isInstanceOf(Boolean.class);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void testMapBodyNotUsedAsContextByDefault() {
        Object result = template.requestBody("direct:targeted-boolean-header",
                Map.of("targetingKey", "user-789", "customer_tier", "ENTERPRISE"));
        assertThat(result).isInstanceOf(Boolean.class);
        assertThat(result).isEqualTo(false);
    }

    @Test
    void testEvaluationDetailsHeaders() {
        Exchange exchange = template.request("direct:boolean-flag", e -> e.getMessage().setBody("ignored"));
        assertThat(exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_VARIANT, String.class))
                .isNotNull();
        assertThat(exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_REASON, String.class))
                .isNotNull();
        assertThat(exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_ERROR_CODE))
                .isNull();
    }

    @Test
    void testStaleErrorHeaderClearedOnSuccess() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:stale-header-test")
                        .to("openfeature:test-stale?flagKey=enrichment-enabled&flagsResource=" + FLAGS_RESOURCE);
            }
        });

        Exchange exchange = template.request("direct:stale-header-test", e -> {
            e.getMessage().setBody("ignored");
            e.getMessage().setHeader(OpenFeatureConstants.EVALUATION_ERROR_CODE, "FLAG_NOT_FOUND");
        });
        assertThat(exchange.getMessage().getBody()).isEqualTo(true);
        assertThat(exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_ERROR_CODE)).isNull();
    }

    @Test
    void testToValuePreservesLargeLong() {
        long largeLong = Long.MAX_VALUE;
        MutableContext ctx = OpenFeatureEndpoint.buildMutableContext(
                "user-1", Map.of("signupTs", largeLong));
        Value value = ctx.getValue("signupTs");
        assertThat(value).isNotNull();
        assertThat(value.asLong()).isEqualTo(largeLong);
    }

    @Test
    void testToValueHandlesFloat() {
        MutableContext ctx = OpenFeatureEndpoint.buildMutableContext(
                "user-1", Map.of("score", 3.14f));
        Value value = ctx.getValue("score");
        assertThat(value).isNotNull();
        assertThat(value.asDouble()).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void testToValueHandlesNestedMap() {
        Map<String, Object> nested = Map.of("city", "Berlin", "zip", 10115);
        MutableContext ctx = OpenFeatureEndpoint.buildMutableContext(
                "user-1", Map.of("address", nested));
        Value value = ctx.getValue("address");
        assertThat(value).isNotNull();
        assertThat(value.isStructure()).isTrue();
        assertThat(value.asStructure().getValue("city").asString()).isEqualTo("Berlin");
        assertThat(value.asStructure().getValue("zip").asInteger()).isEqualTo(10115);
    }

    @Test
    void testToValueHandlesList() {
        MutableContext ctx = OpenFeatureEndpoint.buildMutableContext(
                "user-1", Map.of("tags", List.of("premium", "beta")));
        Value value = ctx.getValue("tags");
        assertThat(value).isNotNull();
        assertThat(value.isList()).isTrue();
        assertThat(value.asList()).hasSize(2);
        assertThat(value.asList().get(0).asString()).isEqualTo("premium");
    }

    @Test
    void testFailedEndpointStartDoesNotAffectSharedDomain() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:shared-domain")
                        .to("openfeature:shared?flagKey=enrichment-enabled&flagsResource=" + FLAGS_RESOURCE);
            }
        });

        Object result = template.requestBody("direct:shared-domain", "ignored");
        assertThat(result).isEqualTo(true);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:bad-endpoint")
                            .to("openfeature:shared?flagKey=enrichment-enabled&provider=#nonexistent");
                }
            });
        } catch (Exception e) {
            // Expected — the provider bean doesn't exist
        }

        result = template.requestBody("direct:shared-domain", "ignored");
        assertThat(result).isEqualTo(true);
    }
}
