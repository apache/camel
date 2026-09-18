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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openai.OpenAIConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = OpenAIExternalServiceTestSupport.ENABLE_LIVE_TESTS, matches = "true",
                         disabledReason = "Set -Dopenai.live.tests=true and configure an OpenAI moderation endpoint")
public class OpenAIModerationExternalServiceIT extends OpenAIExternalServiceTestSupport {

    private static final String MODERATION_MODEL = "openai.live.moderation.model";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:moderate").toF("openai:moderation?moderationModel=%s", requiredProperty(MODERATION_MODEL));
            }
        };
    }

    @Test
    void moderationReturnsAVerdictAndPreservesTheBody() {
        String input = "Apache Camel is an integration framework.";
        Exchange result = template.request("direct:moderate", exchange -> exchange.getIn().setBody(input));

        assertThat(result.getException())
                .as("The configured service must return a valid OpenAI /v1/moderations response")
                .isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(input);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED, Boolean.class)).isNotNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL, String.class)).isNotBlank();
    }

    @Test
    void imageAndTextShareOneVerdict() throws Exception {
        byte[] image = bluePng();
        Exchange result = template.request("direct:moderate", exchange -> {
            exchange.getIn().setBody(image);
            exchange.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "image/png");
            exchange.getIn().setHeader(OpenAIConstants.MODERATION_TEXT, "A plain blue square.");
        });

        assertThat(result.getException())
                .as("The configured moderation model must accept image input, for example omni-moderation-latest")
                .isNull();
        assertThat(result.getMessage().getBody()).isSameAs(image);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED, Boolean.class)).isFalse();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verdicts
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS, List.class);
        assertThat(verdicts).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> inputTypes = (Map<String, List<String>>) verdicts.get(0)
                .get(OpenAIConstants.MODERATION_RESULT_CATEGORY_APPLIED_INPUT_TYPES);
        assertThat(inputTypes.get("violence")).contains("text", "image");
        assertThat(inputTypes.get("hate")).containsExactly("text");
    }

    private static byte[] bluePng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
