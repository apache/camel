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

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPipelineScaffoldToolsTest {

    private AiPipelineScaffoldTools tools;

    @BeforeEach
    void setUp() {
        tools = new AiPipelineScaffoldTools();
    }

    @Test
    void shouldRequirePipelineType() {
        assertThatThrownBy(() -> tools.camel_ai_pipeline_scaffold(null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("pipelineType is required");
    }

    @Test
    void shouldRejectUnknownPipelineType() {
        assertThatThrownBy(() -> tools.camel_ai_pipeline_scaffold("unknown", null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Unknown pipeline type");
    }

    @Test
    void shouldRejectUnknownDocumentProcessor() {
        assertThatThrownBy(() -> tools.camel_ai_pipeline_scaffold("summarization", "invalid", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Unknown document processor");
    }

    @Test
    void shouldRejectUnknownDocumentSource() {
        assertThatThrownBy(() -> tools.camel_ai_pipeline_scaffold("summarization", "docling", "ftp", null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Unknown document source");
    }

    @ParameterizedTest
    @ValueSource(strings = { "summarization", "extraction", "rag", "classification" })
    void shouldGenerateRouteForEachPipelineType(String type) {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold(type, "docling", "file", null, null);

        assertThat(result.yamlRoute()).isNotBlank();
        assertThat(result.yamlRoute()).contains("route:");
        assertThat(result.yamlRoute()).contains("docling:convert");
        assertThat(result.yamlRoute()).contains("aws-bedrock:");
        assertThat(result.applicationProperties()).isNotBlank();
        assertThat(result.description()).isNotBlank();
    }

    @Test
    void shouldGenerateSummarizationWithDocling() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("summarization", "docling", "file", null, null);

        assertThat(result.yamlRoute())
                .contains("ai-summarization")
                .contains("docling:convert")
                .contains("CONVERT_TO_MARKDOWN")
                .contains("converse")
                .contains("buildSummarizationPrompt");

        assertThat(result.applicationProperties())
                .contains("docling.server.url")
                .contains("document.input.dir");
    }

    @Test
    void shouldGenerateExtractionWithTextract() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("extraction", "textract", "s3", null, null);

        assertThat(result.yamlRoute())
                .contains("ai-extraction")
                .contains("aws2-textract:")
                .contains("detectDocumentText")
                .contains("buildExtractionPrompt");

        assertThat(result.applicationProperties())
                .contains("document.s3.bucket")
                .doesNotContain("docling.server.url");
    }

    @Test
    void shouldGenerateRagPipelineWithTwoRoutes() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("rag", "docling", "file", null, null);

        assertThat(result.yamlRoute())
                .contains("rag-ingestion")
                .contains("rag-query")
                .contains("langchain4j-embeddings:")
                .contains("split:");
    }

    @Test
    void shouldGenerateClassificationWithChoiceRouter() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("classification", "docling", "file", null, null);

        assertThat(result.yamlRoute())
                .contains("ai-classification")
                .contains("choice:")
                .contains("handle-invoice")
                .contains("handle-contract");
    }

    @Test
    void shouldSupportCombinedProcessor() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("summarization", "combined", "file", null, null);

        assertThat(result.yamlRoute())
                .contains("docling:convert")
                .contains("aws2-textract:");

        assertThat(result.applicationProperties())
                .contains("docling.server.url");
    }

    @Test
    void shouldUseCustomModelAndRegion() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold(
                        "summarization", "docling", "file", "amazon.nova-pro-v1:0", "eu-west-1");

        assertThat(result.yamlRoute())
                .contains("amazon.nova-pro-v1:0")
                .contains("eu-west-1");

        assertThat(result.applicationProperties())
                .contains("eu-west-1");
    }

    @Test
    void shouldGenerateS3SourceEndpoint() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("summarization", "docling", "s3", null, null);

        assertThat(result.yamlRoute())
                .contains("aws2-s3:")
                .contains("document.s3.bucket");
    }

    @Test
    void shouldGenerateUrlSourceEndpoint() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("summarization", "docling", "url", null, null);

        assertThat(result.yamlRoute())
                .contains("direct:process-url");
    }

    @Test
    void shouldUseDefaultsWhenOptionalParamsAreNull() {
        AiPipelineScaffoldTools.ScaffoldResult result
                = tools.camel_ai_pipeline_scaffold("summarization", null, null, null, null);

        assertThat(result.yamlRoute())
                .contains("docling:convert")
                .contains("us-east-1");

        assertThat(result.description())
                .contains("Docling");
    }
}
