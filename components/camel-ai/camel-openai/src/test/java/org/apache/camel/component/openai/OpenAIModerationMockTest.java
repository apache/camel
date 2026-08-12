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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.openai.models.moderations.ModerationCreateResponse;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIModerationMockTest extends CamelTestSupport {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenModeration("Apache Camel is an integration framework")
            .replyWithModerationAllowed()
            .end()
            .whenModeration("I hate everyone")
            .replyWithModerationFlagged("hate", 0.92)
            .end()
            .whenModeration("Another harmless sentence")
            .replyWithModerationScore("violence", 0.01)
            .end()
            .whenModeration("Legacy model input")
            .replyWithoutIllicitCategories()
            .end()
            .whenModeration("42")
            .replyWithModerationAllowed()
            .end()
            .whenModeration("Provider returns no verdict")
            .replyWithoutModerationResult()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:moderation")
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");

                from("direct:moderation-store-response")
                        .to("openai:moderation?apiKey=dummy&storeFullResponse=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:moderation-explicit-model")
                        .to("openai:moderation?apiKey=dummy&moderationModel=omni-moderation-2024-09-26&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:split-verdicts")
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1")
                        .split(header(OpenAIConstants.MODERATION_RESULTS))
                        .choice()
                        .when(simple("${body[flagged]}"))
                        .to("mock:quarantine")
                        .otherwise()
                        .to("mock:downstream")
                        .end();

                from("direct:score-threshold")
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1")
                        .choice()
                        .when(simple("${header.CamelOpenAIModerationCategoryScores[hate]} > 0.85"))
                        .to("mock:review")
                        .otherwise()
                        .to("mock:score-accepted")
                        .end();

                from("direct:guard")
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1")
                        .choice()
                        .when(header(OpenAIConstants.MODERATION_FLAGGED).isEqualTo(true))
                        .setBody(constant("Your message violates our usage policy."))
                        .otherwise()
                        .setBody(constant("accepted"))
                        .end();
            }
        };
    }

    @Test
    void testAllowedInput() {
        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        // the body is passed through unchanged
        assertThat(result.getMessage().getBody()).isEqualTo("Apache Camel is an integration framework");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL)).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Boolean> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, Map.class);
        assertThat(categories).containsEntry("hate", false).containsEntry("violence", false);

        @SuppressWarnings("unchecked")
        Map<String, Double> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, Map.class);
        assertThat(scores).containsKeys("hate", "self-harm/intent", "violence/graphic");
        assertThat(scores.get("hate")).isEqualTo(0.0);
        // the omni-moderation models do report the illicit categories
        assertThat(categories).containsKeys("illicit", "illicit/violent");
    }

    @Test
    void testFlaggedInput() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody("I hate everyone"));

        assertThat(result.getMessage().getBody()).isEqualTo("I hate everyone");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, Map.class);
        assertThat(categories).containsEntry("hate", true).containsEntry("violence", false);

        @SuppressWarnings("unchecked")
        Map<String, Double> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, Map.class);
        assertThat(scores.get("hate")).isEqualTo(0.92);
    }

    @Test
    void testScoredButNotFlaggedInput() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody("Another harmless sentence"));

        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, Map.class);
        assertThat(categories).containsEntry("violence", false);

        @SuppressWarnings("unchecked")
        Map<String, Double> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, Map.class);
        assertThat(scores.get("violence")).isEqualTo(0.01);
    }

    @Test
    void testBatchModeration() {
        List<String> inputs = List.of("Apache Camel is an integration framework", "I hate everyone");

        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(inputs));

        assertThat(result.getMessage().getBody()).isEqualTo(inputs);
        // flagged is true when at least one input of the batch was flagged
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verdicts
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS, List.class);
        assertThat(verdicts).hasSize(2);

        assertThat(verdicts.get(0))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "Apache Camel is an integration framework")
                .containsEntry(OpenAIConstants.MODERATION_RESULT_FLAGGED, false);
        assertThat(verdicts.get(1))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "I hate everyone")
                .containsEntry(OpenAIConstants.MODERATION_RESULT_FLAGGED, true);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> flaggedCategories
                = (Map<String, Boolean>) verdicts.get(1).get(OpenAIConstants.MODERATION_RESULT_CATEGORIES);
        assertThat(flaggedCategories).containsEntry("hate", true);

        @SuppressWarnings("unchecked")
        Map<String, Double> flaggedScores
                = (Map<String, Double>) verdicts.get(1).get(OpenAIConstants.MODERATION_RESULT_CATEGORY_SCORES);
        assertThat(flaggedScores.get("hate")).isEqualTo(0.92);

        // the plain maps belong to the single-input shape only
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES)).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES)).isNull();
    }

    @Test
    void testBatchVerdictsCanBeSplitAndRouted() throws Exception {
        MockEndpoint quarantine = getMockEndpoint("mock:quarantine");
        MockEndpoint downstream = getMockEndpoint("mock:downstream");
        quarantine.expectedMessageCount(1);
        downstream.expectedMessageCount(1);

        template.sendBody("direct:split-verdicts",
                List.of("Apache Camel is an integration framework", "I hate everyone"));

        MockEndpoint.assertIsSatisfied(context);
        assertThat(quarantine.getExchanges().get(0).getMessage().getBody(Map.class))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "I hate everyone");
        assertThat(downstream.getExchanges().get(0).getMessage().getBody(Map.class))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "Apache Camel is an integration framework");
    }

    @Test
    void testCategoryScoreThresholdInSimpleExpression() throws Exception {
        MockEndpoint review = getMockEndpoint("mock:review");
        MockEndpoint accepted = getMockEndpoint("mock:score-accepted");
        review.expectedMessageCount(1);
        accepted.expectedMessageCount(1);

        // hate scores 0.92, above the 0.85 threshold
        template.sendBody("direct:score-threshold", "I hate everyone");
        // hate scores 0.0
        template.sendBody("direct:score-threshold", "Apache Camel is an integration framework");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testStoreFullResponse() {
        Exchange result = template.request("direct:moderation-store-response",
                e -> e.getIn().setBody("I hate everyone"));

        Object response = result.getProperty(OpenAIConstants.MODERATION_RESPONSE);
        assertThat(response).isInstanceOf(ModerationCreateResponse.class);

        ModerationCreateResponse moderationResponse = (ModerationCreateResponse) response;
        assertThat(moderationResponse.results()).hasSize(1);
        // the stored response must stay usable, not just deserializable
        assertThat(moderationResponse.results().get(0).flagged()).isTrue();
        assertThat(moderationResponse.results().get(0).categories().hate()).isTrue();
        // guards the mock against drifting from the payload the API actually returns
        assertThat(moderationResponse.isValid()).isTrue();
    }

    @Test
    void testModelFromEndpointOptionAndHeader() {
        Exchange fromOption = template.request("direct:moderation-explicit-model",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));
        assertThat(fromOption.getMessage().getHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL))
                .isEqualTo("omni-moderation-2024-09-26");

        // the header wins over the endpoint option
        Exchange fromHeader = template.request("direct:moderation-explicit-model", e -> {
            e.getIn().setBody("Apache Camel is an integration framework");
            e.getIn().setHeader(OpenAIConstants.MODERATION_MODEL, "omni-moderation-latest");
        });
        assertThat(fromHeader.getMessage().getHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL))
                .isEqualTo("omni-moderation-latest");

        // and the default applies when neither is set
        Exchange fromDefault = template.request("direct:moderation",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));
        assertThat(fromDefault.getMessage().getHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL))
                .isEqualTo("omni-moderation-latest");
    }

    @Test
    void testProviderWithoutIllicitCategories() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody("Legacy model input"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, Map.class);
        // illicit is optional in the API model, so a provider may omit it
        assertThat(categories).doesNotContainKeys("illicit", "illicit/violent");
        assertThat(categories).containsEntry("hate", false);

        @SuppressWarnings("unchecked")
        Map<String, Double> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, Map.class);
        assertThat(scores).containsKey("illicit");
    }

    @Test
    void testNonStringBodyIsConverted() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(42));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);
        // the body is passed through untouched, not replaced by the converted input
        assertThat(result.getMessage().getBody()).isEqualTo(42);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, Map.class);
        assertThat(categories).containsEntry("hate", false);
    }

    @Test
    void testSingleElementListKeepsTheBatchShape() {
        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody(List.of("Apache Camel is an integration framework")));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);

        // a List body is a batch even with one element, so batch processors need no special case
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verdicts
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS, List.class);
        assertThat(verdicts).hasSize(1);
        assertThat(verdicts.get(0)).containsEntry(OpenAIConstants.MODERATION_RESULT_FLAGGED, false);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES)).isNull();
    }

    @Test
    void testStringBodyExposesPlainMaps() {
        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES)).isInstanceOf(Map.class);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES)).isInstanceOf(Map.class);

        // the results header is always present, so a route can use one shape everywhere
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verdicts
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS, List.class);
        assertThat(verdicts).hasSize(1);
    }

    @Test
    void testPartialBatchVerdictFailsClosed() {
        List<String> inputs = List.of("Apache Camel is an integration framework", "Provider returns no verdict");

        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(inputs));

        // one verdict short of the batch must fail as well, not just an empty response
        assertThat(result.getException())
                .isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("Moderation returned 1 result(s) for 2 input(s)");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS)).isNull();
    }

    @Test
    void testEmptyListFails() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(List.of()));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No input text provided for moderation");
    }

    @Test
    void testListWithNullElementFails() {
        List<String> inputs = new ArrayList<>();
        inputs.add("Apache Camel is an integration framework");
        inputs.add(null);

        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(inputs));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null elements");
    }

    @Test
    void testMissingBodyFails() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody(null));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No input text provided for moderation");
    }

    @Test
    void testGuardRouteRejectsFlaggedInput() {
        Exchange rejected = template.request("direct:guard", e -> e.getIn().setBody("I hate everyone"));
        assertThat(rejected.getMessage().getBody(String.class)).isEqualTo("Your message violates our usage policy.");

        Exchange accepted = template.request("direct:guard",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));
        assertThat(accepted.getMessage().getBody(String.class)).isEqualTo("accepted");
    }
}
