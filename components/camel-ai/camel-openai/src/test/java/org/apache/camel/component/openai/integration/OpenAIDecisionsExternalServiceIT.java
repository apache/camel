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
package org.apache.camel.component.openai.integration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.models.decisions.DecisionInputText;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = OpenAIExternalServiceTestSupport.ENABLE_LIVE_TESTS, matches = "true",
                         disabledReason = "Set -Dopenai.live.tests=true and configure a Decisions API service and model")
public class OpenAIDecisionsExternalServiceIT extends OpenAIExternalServiceTestSupport {
    private static final String DECISIONS_MODEL = "openai.live.decisions.model";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:decisions").to("openai:decisions?requestTimeout=60000&maxRetries=0");
            }
        };
    }

    @Test
    void textBatchReturnsNativePredicateChoiceAndScore() {
        var request = DecisionCreateParams.builder().model(requiredProperty(DECISIONS_MODEL))
                .input("My invoice contains a duplicate charge. Please help.")
                .addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name("request").instructions("Does the message ask for assistance?").build())
                .addQuestion(DecisionCreateParams.Question.Choice.builder().name("category")
                        .instructions("Which team should assess this message?")
                        .addChoice(DecisionChoiceOption.builder().value("billing").description("Invoices and charges").build())
                        .addChoice(DecisionChoiceOption.builder().value("technical").description("Product defects").build())
                        .build())
                .addQuestion(DecisionCreateParams.Question.Score.builder().name("urgency")
                        .instructions("How urgent is the request?")
                        .addLevel(DecisionCreateParams.Question.Score.Level.builder().label("routine").build())
                        .addLevel(DecisionCreateParams.Question.Score.Level.builder().label("urgent").build()).build())
                .build();
        Decision response = template.requestBody("direct:decisions", request, Decision.class);
        assertThat(response.model()).isNotBlank();
        assertThat(response.answers()).hasSize(3);
        assertThat(response.answers().get(0).asPredicate().probability()).isBetween(0.0, 1.0);
        assertThat(response.answers().get(1).asChoice().choice().asString()).isIn("billing", "technical");
        assertThat(response.answers().get(2).asScore().score()).isBetween(0.0, 1.0);
    }

    @Test
    void mixedTextAndInlineImageAreAcceptedWithoutTranscodingServices() throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var request = DecisionCreateParams.builder().model(requiredProperty(DECISIONS_MODEL))
                .inputOfDecisionInputMessages(List.of(DecisionInputMessage.builder().contentOfParts(List.of(
                        DecisionInputPart.ofInputText(DecisionInputText.builder().text("Inspect this image.").build()),
                        DecisionInputPart.ofInputImage(DecisionInputImage.builder()
                                .imageUrl("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()))
                                .build())))
                        .build()))
                .addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name("dark").instructions("Is the image predominantly dark?").build())
                .build();
        Decision response = template.requestBody("direct:decisions", request, Decision.class);
        assertThat(response.model()).isNotBlank();
        assertThat(response.answers()).hasSize(1);
        assertThat(response.answers().get(0).asPredicate().probability()).isBetween(0.0, 1.0);
    }
}
