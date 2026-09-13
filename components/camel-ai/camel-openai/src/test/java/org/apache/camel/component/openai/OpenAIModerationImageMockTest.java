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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIModerationImageMockTest extends CamelTestSupport {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };
    private static final byte[] JPEG = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0 };

    @TempDir
    Path tempDir;

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenImageModeration()
            .assertModerationImageUrl(url -> assertThat(url)
                    .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG)))
            .replyWithModerationAllowed()
            .end()
            .whenImageModeration("Look what I found")
            .assertModerationImageUrl(url -> assertThat(url).startsWith("data:image/jpeg;base64,"))
            .replyWithModerationFlagged("violence", 0.91)
            .end()
            .whenImageModeration("Provider returns no verdict")
            .replyWithoutModerationResult()
            .end()
            .whenModeration("Apache Camel is an integration framework")
            .replyWithModerationAllowed()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:moderation")
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");

                from("direct:moderation-no-stream-caching")
                        .noStreamCaching()
                        .to("openai:moderation?apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testImageAlone() {
        Exchange result = template.request("direct:moderation", e -> {
            e.getIn().setBody(PNG);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "image/png");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isSameAs(PNG);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(false);

        List<Map<String, Object>> verdicts = verdicts(result);
        assertThat(verdicts).hasSize(1);
        // there is no text to report back for an image moderated on its own
        assertThat(verdicts.get(0)).containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, null);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> inputTypes = (Map<String, List<String>>) verdicts.get(0)
                .get(OpenAIConstants.MODERATION_RESULT_CATEGORY_APPLIED_INPUT_TYPES);
        assertThat(inputTypes).containsEntry("sexual", List.of("image")).containsEntry("hate", List.of());
    }

    @Test
    void testImageFileWithText() throws Exception {
        File image = Files.write(tempDir.resolve("found.jpg"), JPEG).toFile();

        Exchange result = template.request("direct:moderation", e -> {
            e.getIn().setBody(image);
            e.getIn().setHeader(OpenAIConstants.MODERATION_TEXT, "Look what I found");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isSameAs(image);
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_FLAGGED)).isEqualTo(true);

        // the text and the image share one verdict, which carries the text as its input
        List<Map<String, Object>> verdicts = verdicts(result);
        assertThat(verdicts).hasSize(1);
        assertThat(verdicts.get(0))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "Look what I found")
                .containsEntry(OpenAIConstants.MODERATION_RESULT_FLAGGED, true);

        @SuppressWarnings("unchecked")
        Map<String, Double> scores
                = result.getMessage().getHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, Map.class);
        assertThat(scores.get("violence")).isEqualTo(0.91);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> inputTypes = (Map<String, List<String>>) verdicts.get(0)
                .get(OpenAIConstants.MODERATION_RESULT_CATEGORY_APPLIED_INPUT_TYPES);
        assertThat(inputTypes)
                .containsEntry("violence", List.of("text", "image"))
                .containsEntry("hate", List.of("text"));
    }

    @Test
    void testStreamCachedImageStaysReadable() {
        Exchange result = template.request("direct:moderation", e -> {
            e.getIn().setBody(new ByteArrayInputStream(PNG));
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "image/png");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(PNG);
    }

    @Test
    void testPlainStreamImageStaysReadable() {
        Exchange result = template.request("direct:moderation-no-stream-caching", e -> {
            e.getIn().setBody(new ByteArrayInputStream(PNG));
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "image/png");
        });

        assertThat(result.getException()).isNull();
        // a stream can be read only once, so the body is kept as the bytes that were moderated
        assertThat(result.getMessage().getBody()).isEqualTo(PNG);
    }

    @Test
    void testMissingImageVerdictFailsClosed() {
        Exchange result = template.request("direct:moderation", e -> {
            e.getIn().setBody(PNG);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "image/png");
            e.getIn().setHeader(OpenAIConstants.MODERATION_TEXT, "Provider returns no verdict");
        });

        assertThat(result.getException())
                .isInstanceOf(CamelExchangeException.class)
                .hasMessageContaining("Moderation returned 0 result(s) for 1 input(s)");
        assertThat(result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS)).isNull();
    }

    @Test
    void testTextHeaderIsIgnoredForTextBody() {
        Exchange result = template.request("direct:moderation", e -> {
            e.getIn().setBody("Apache Camel is an integration framework");
            e.getIn().setHeader(OpenAIConstants.MODERATION_TEXT, "Look what I found");
        });

        assertThat(result.getException()).isNull();
        assertThat(verdicts(result).get(0))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "Apache Camel is an integration framework");
    }

    @Test
    void testBinaryBodyWithoutImageTypeIsModeratedAsText() {
        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody("Apache Camel is an integration framework".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.getException()).isNull();
        assertThat(verdicts(result).get(0))
                .containsEntry(OpenAIConstants.MODERATION_RESULT_INPUT, "Apache Camel is an integration framework");
    }

    @Test
    void testImageInListFails() throws Exception {
        File image = Files.write(tempDir.resolve("found.png"), PNG).toFile();

        Exchange result = template.request("direct:moderation",
                e -> e.getIn().setBody(List.of("Apache Camel is an integration framework", image)));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Split the list and moderate each image on its own");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> verdicts(Exchange result) {
        return result.getMessage().getHeader(OpenAIConstants.MODERATION_RESULTS, List.class);
    }
}
