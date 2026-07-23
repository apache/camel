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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteCostEstimateToolsTest {

    private RouteCostEstimateTools tools;

    @BeforeEach
    void setUp() {
        tools = new RouteCostEstimateTools();
    }

    private static final String AI_PIPELINE_ROUTE = """
            - route:
                id: ai-summarization
                from:
                  uri: "file:documents"
                steps:
                  - to: "docling:convert"
                  - to: "aws-bedrock:label"
                  - to: "aws2-s3:output-bucket"
            """;

    private static final String TEXTRACT_ROUTE = """
            - route:
                from:
                  uri: "aws2-s3:input-bucket"
                steps:
                  - to: "aws2-textract:detect"
                  - to: "aws-bedrock:label"
            """;

    private static final String SIMPLE_ROUTE = """
            - route:
                from:
                  uri: "timer:tick"
                steps:
                  - to: "log:out"
            """;

    @Test
    void shouldRequireRouteContent() {
        assertThatThrownBy(() -> tools.camel_route_cost_estimate(null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Route content is required");
    }

    @Test
    void shouldEstimateCostForAiPipeline() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(AI_PIPELINE_ROUTE, 100, 5, 1000, 500);

        assertThat(result).isNotNull();
        assertThat(result.costBreakdown()).isNotNull();
        assertThat(result.costBreakdown()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.summary().mostExpensiveComponent()).isEqualTo("aws-bedrock");
        assertThat(result.projection().messagesPerHour()).isEqualTo(100);
        assertThat(result.disclaimer()).isNotBlank();
    }

    @Test
    void shouldIdentifyBedrockAsMostExpensive() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(TEXTRACT_ROUTE, null, null, null, null);

        assertThat(result.costBreakdown()).isNotNull();
        assertThat(result.costBreakdown().get(0).scheme()).isEqualTo("aws-bedrock");
    }

    @Test
    void shouldReportNoPricedComponentsForSimpleRoute() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(SIMPLE_ROUTE, null, null, null, null);

        assertThat(result.costBreakdown()).isNull();
        assertThat(result.summary().note()).contains("No pay-per-use components");
    }

    @Test
    void shouldIncludeDoclingAsFree() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(AI_PIPELINE_ROUTE, null, null, null, null);

        assertThat(result.costBreakdown())
                .anyMatch(c -> "docling".equals(c.scheme()) && c.estimatedCostPerExecution() == 0);
    }

    @Test
    void shouldProjectMonthlyCosts() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(TEXTRACT_ROUTE, 1000, 10, 2000, 1000);

        assertThat(result.projection()).isNotNull();
        assertThat(result.projection().monthly()).startsWith("$");
        assertThat(result.projection().messagesPerHour()).isEqualTo(1000);
    }

    @Test
    void shouldProvideOptimizationTips() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(AI_PIPELINE_ROUTE, null, null, null, null);

        assertThat(result.optimizationTips()).isNotNull();
        assertThat(result.optimizationTips()).isNotEmpty();
    }

    @Test
    void shouldExtractSchemesCorrectly() {
        List<String> schemes = tools.extractSchemes(AI_PIPELINE_ROUTE);

        assertThat(schemes).contains("docling", "aws-bedrock", "aws2-s3");
    }

    @Test
    void shouldUseDefaultsForNullParams() {
        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(AI_PIPELINE_ROUTE, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.projection().messagesPerHour()).isEqualTo(100);
    }

    @Test
    void shouldExtractSchemesFromXmlRoutes() {
        String xmlRoute = """
                <route>
                  <from uri="aws2-s3:bucket"/>
                  <to uri="aws-bedrock:label"/>
                </route>
                """;

        List<String> schemes = tools.extractSchemes(xmlRoute);

        assertThat(schemes).contains("aws2-s3", "aws-bedrock");
    }

    @Test
    void shouldHandleTextractAndDoclingCombined() {
        String combinedRoute = """
                - route:
                    from:
                      uri: "file:docs"
                    steps:
                      - to: "docling:convert"
                      - to: "aws2-textract:detect"
                      - to: "aws-bedrock:label"
                """;

        RouteCostEstimateTools.CostEstimateResult result
                = tools.camel_route_cost_estimate(combinedRoute, null, null, null, null);

        assertThat(result.optimizationTips())
                .anyMatch(t -> t.contains("Textract") && t.contains("Docling"));
    }
}
