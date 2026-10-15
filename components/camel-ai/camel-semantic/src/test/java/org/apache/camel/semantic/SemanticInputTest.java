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
package org.apache.camel.semantic;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticInputTest {
    @Test
    void imageBodiesNormalizeWithoutLosingTheRoutePayload() throws Exception {
        byte[] bytes = new byte[] { 1, 2, 3 };
        try (var context = new DefaultCamelContext()) {
            context.start();
            for (Object body : List.of(bytes, "AQID", new ByteArrayInputStream(bytes),
                    new FilterInputStream(new ByteArrayInputStream(bytes)) {
                        @Override
                        public boolean markSupported() {
                            return false;
                        }
                    })) {
                var exchange = new DefaultExchange(context);
                exchange.getMessage().setBody(body);
                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "image/png");
                assertThat(SemanticInput.body(exchange))
                        .isEqualTo(SemanticInput.ofParts(List.of(SemanticInput.image("data:image/png;base64,AQID"))));
                if (exchange.getMessage().getBody() instanceof StreamCache cache) {
                    assertThat(exchange.getMessage().getBody(byte[].class)).containsExactly(bytes);
                    cache.reset();
                    assertThat(SemanticInput.body(exchange))
                            .isEqualTo(SemanticInput.ofParts(List.of(SemanticInput.image(bytes, "image/png"))));
                } else {
                    assertThat(exchange.getMessage().getBody()).isSameAs(body);
                }
            }
        }
    }

    @Test
    void plainBase64IsTextUnlessAnImageIsExplicit() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("AQID");
            assertThat(SemanticInput.body(exchange)).isEqualTo("AQID");
            exchange.getMessage().setBody("data:image/png;base64,AQID");
            assertThat(SemanticInput.isMedia(SemanticInput.body(exchange))).isTrue();
        }
    }

    @Test
    void audioAndVideoContentTypesRejectEvenStringBodies() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("AQID");
            for (String type : List.of("audio/wav", "video/mp4")) {
                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, type);
                assertThatThrownBy(() -> SemanticInput.body(exchange)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not supported");
            }
        }
    }

    @Test
    void validatesEveryExplicitPartAgainstExpertCapabilities() {
        var input = SemanticInput
                .ofParts(List.of(SemanticInput.text("Inspect this"), SemanticInput.image("data:image/png;base64,AA==")));
        assertThatCode(() -> SemanticInput.validate(input, Set.of(InputType.TEXT, InputType.IMAGE))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SemanticInput.validate(input, Set.of(InputType.TEXT, InputType.STRUCTURED)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported media");
        assertThatThrownBy(() -> SemanticInput.validate(input, Set.of(InputType.IMAGE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SemanticInput.parts(input)).hasSize(2);
    }

    @Test
    void preservesOrdinaryApplicationStateAndText() {
        assertThatCode(() -> SemanticInput.validate(Map.of("custom", new Object()), Set.of(InputType.STRUCTURED)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SemanticInput.validate(Map.of("type", "image", "url", "https://example.com"),
                Set.of(InputType.STRUCTURED))).doesNotThrowAnyException();
        assertThatCode(() -> SemanticInput.validate("data:audio/wav;base64,AA==", Set.of(InputType.TEXT)))
                .doesNotThrowAnyException();
        assertThatCode(() -> SemanticInput.validate(List.of(Map.of("value", true), 0.5, "text"), Set.of(InputType.STRUCTURED)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownPartsAndAmbiguousEnvelopes() {
        for (Object input : List.of(Map.of(SemanticInput.PARTS, List.of(Map.of("type", "audio", "data", "raw"))),
                Map.of(SemanticInput.PARTS, List.of(SemanticInput.text("text")), "extra", "value"))) {
            assertThatThrownBy(() -> SemanticInput.validate(input, Set.of(InputType.TEXT, InputType.IMAGE)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
