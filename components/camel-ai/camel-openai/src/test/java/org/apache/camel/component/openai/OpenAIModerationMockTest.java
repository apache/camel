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
        List<Map<String, Boolean>> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, List.class);
        assertThat(categories).hasSize(2);
        assertThat(categories.get(0)).containsEntry("hate", false);
        assertThat(categories.get(1)).containsEntry("hate", true);

        @SuppressWarnings("unchecked")
        List<Map<String, Double>> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, List.class);
        assertThat(scores).hasSize(2);
        assertThat(scores.get(1).get("hate")).isEqualTo(0.92);
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

        // a List body yields List headers even for one element, so batch processors need no special case
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES)).isInstanceOf(List.class);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES)).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Boolean>> categories
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES, List.class);
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0)).containsEntry("hate", false);
    }

    @Test
    void testStringBodyKeepsTheSingleShape() {
        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORIES)).isInstanceOf(Map.class);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES)).isInstanceOf(Map.class);
    }

    @Test
    void testMissingVerdictFailsClosed() {
        Exchange result = template.request("direct:moderation", e -> e.getIn().setBody("Provider returns no verdict"));

        // no verdict must fail the exchange rather than leave the flag false and let the message through
        assertThat(result.getException())
                .isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("Moderation returned 0 result(s) for 1 input(s)");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isNull();
    }

    @Test
    void testGuardRouteDoesNotLetContentThroughWithoutVerdict() {
        Exchange result = template.request("direct:guard", e -> e.getIn().setBody("Provider returns no verdict"));

        assertThat(result.getException()).isInstanceOf(CamelExchangeException.class);
        assertThat(result.getMessage().getBody(String.class)).isNotEqualTo("accepted");
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
