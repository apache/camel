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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAIImageEditMockTest extends CamelTestSupport {

    private static final byte[] SOURCE_IMAGE = "FAKE-PNG-SOURCE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_SOURCE_IMAGE = "FAKE-PNG-SOURCE-TWO".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MASK_IMAGE = "FAKE-PNG-MASK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EDITED_IMAGE = "FAKE-PNG-EDITED".getBytes(StandardCharsets.UTF_8);

    @TempDir
    File tempDir;

    private final AtomicReference<byte[]> lastRequest = new AtomicReference<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenImageEdit()
            .replyWithImage(EDITED_IMAGE)
            .assertImageRequest(lastRequest::set)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:edit")
                        .to("openai:image-edit?imageModel=gpt-image-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:edit-with-prompt-option")
                        .to("openai:image-edit?imageModel=gpt-image-1&imagePrompt=Add a red SALE banner"
                            + "&imageInputFidelity=high&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:edit-no-model")
                        .to("openai:image-edit?apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testEditByteArrayBody() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(EDITED_IMAGE);
        assertThat(result.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("image/png");
        assertThat(fileParts()).isEqualTo(1);
        assertThat(requestBody()).contains("Add a red SALE banner");
        // the API rejects the upload on the content type of the part, not on the file name
        assertThat(contentTypeParts()).containsExactly("image/png");
    }

    @Test
    void testEditFileBody() throws Exception {
        File source = new File(tempDir, "product.png");
        Files.write(source.toPath(), SOURCE_IMAGE);

        Exchange result = template.request("direct:edit-with-prompt-option", e -> e.getIn().setBody(source));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(EDITED_IMAGE);
        assertThat(fileParts()).isEqualTo(1);
        assertThat(requestBody()).contains("product.png").contains("input_fidelity");
        assertThat(contentTypeParts()).containsExactly("image/png");
    }

    @Test
    void testEditInputStreamBody() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(new ByteArrayInputStream(SOURCE_IMAGE));
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(EDITED_IMAGE);
        assertThat(fileParts()).isEqualTo(1);
    }

    @Test
    void testEditWithSeveralReferenceImages() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(List.of(SOURCE_IMAGE, SECOND_SOURCE_IMAGE));
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Combine both products into one scene");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(EDITED_IMAGE);
        assertThat(fileParts()).isEqualTo(2);
    }

    @Test
    void testEditWithMask() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Replace the masked area with a blue sky");
            e.getIn().setHeader(OpenAIConstants.IMAGE_MASK, MASK_IMAGE);
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(byte[].class)).isEqualTo(EDITED_IMAGE);
        // one part for the image and one for the mask
        assertThat(fileParts()).isEqualTo(2);
        assertThat(requestBody()).contains("mask.png");
        assertThat(contentTypeParts()).containsExactly("image/png", "image/png");
    }

    @Test
    void testFilenameComesFromTheFileNameHeader() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(Exchange.FILE_NAME_ONLY, "banner.webp");
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException()).isNull();
        assertThat(requestBody()).contains("banner.webp");
    }

    @Test
    void testFilenameExtensionComesFromTheContentType() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "image/webp");
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException()).isNull();
        assertThat(requestBody()).contains("image.webp");
        assertThat(contentTypeParts()).containsExactly("image/webp");
    }

    @Test
    void testJpegFileBodyKeepsItsContentType() throws Exception {
        File source = new File(tempDir, "product.jpg");
        Files.write(source.toPath(), SOURCE_IMAGE);

        Exchange result = template.request("direct:edit-with-prompt-option", e -> e.getIn().setBody(source));

        assertThat(result.getException()).isNull();
        assertThat(contentTypeParts()).containsExactly("image/jpeg");
    }

    @Test
    void testNonImageContentTypeFallsBackToPng() {
        // the API only accepts png, jpeg and webp, so anything else must not be passed through
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=utf-8");
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException()).isNull();
        assertThat(contentTypeParts()).containsExactly("image/png");
    }

    @Test
    void testMissingPrompt() {
        Exchange result = template.request("direct:edit", e -> e.getIn().setBody(SOURCE_IMAGE));

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edit prompt must be specified");
    }

    @Test
    void testMissingModel() {
        Exchange result = template.request("direct:edit-no-model", e -> {
            e.getIn().setBody(SOURCE_IMAGE);
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image model must be specified");
    }

    @Test
    void testEmptyImageList() {
        Exchange result = template.request("direct:edit", e -> {
            e.getIn().setBody(List.of());
            e.getIn().setHeader(OpenAIConstants.IMAGE_PROMPT, "Add a red SALE banner");
        });

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be an empty list");
    }

    private String requestBody() {
        assertThat(lastRequest.get()).isNotNull();
        return new String(lastRequest.get(), StandardCharsets.ISO_8859_1);
    }

    private List<String> contentTypeParts() {
        return Pattern.compile("Content-Type: (image/[a-z]+)").matcher(requestBody())
                .results()
                .map(match -> match.group(1))
                .toList();
    }

    private int fileParts() {
        String body = requestBody();
        int count = 0;
        int index = body.indexOf("filename=");
        while (index >= 0) {
            count++;
            index = body.indexOf("filename=", index + 1);
        }
        return count;
    }
}
