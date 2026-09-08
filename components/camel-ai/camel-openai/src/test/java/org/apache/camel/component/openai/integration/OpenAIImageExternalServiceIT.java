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
                         disabledReason = "Set -Dopenai.live.tests=true and configure an OpenAI-compatible image service")
public class OpenAIImageExternalServiceIT extends OpenAIExternalServiceTestSupport {

    private static final String IMAGE_MODEL = "openai.live.image.model";

    @Override
    protected RouteBuilder createRouteBuilder() {
        String imageModel = requiredProperty(IMAGE_MODEL);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:generate").toF("openai:image-generation?imageModel=%s&imageOutputFormat=png", imageModel);
                from("direct:edit").toF("openai:image-edit?imageModel=%s&imageOutputFormat=png", imageModel);
            }
        };
    }

    @Test
    void generatedImageCanBeEdited() throws Exception {
        Exchange generated = template.request("direct:generate",
                exchange -> exchange.getIn().setBody("The Apache Camel logo"));
        assertThat(generated.getException()).isNull();
        byte[] image = generated.getMessage().getBody(byte[].class);
        assertThat(image).isNotEmpty();
        writeArtifact("generated.png", image);

        Exchange edited = template.request("direct:edit", exchange -> {
            exchange.getIn().setBody(image);
            exchange.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Change camel color to blue");
        });
        assertThat(edited.getException()).isNull();
        byte[] editedImage = edited.getMessage().getBody(byte[].class);
        assertThat(editedImage).isNotEmpty();
        writeArtifact("edited.png", editedImage);
    }
}
