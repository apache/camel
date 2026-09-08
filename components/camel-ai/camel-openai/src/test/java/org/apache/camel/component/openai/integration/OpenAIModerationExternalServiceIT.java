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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openai.OpenAIConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "openai.live.tests", matches = "true",
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
}
