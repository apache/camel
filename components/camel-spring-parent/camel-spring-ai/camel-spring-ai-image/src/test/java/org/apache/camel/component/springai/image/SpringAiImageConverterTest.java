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
package org.apache.camel.component.springai.image;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SpringAiImageConverterTest extends CamelTestSupport {

    private static final String URL = "https://example.com/generated.png";

    @TempDir
    Path tempDir;

    @BindToRegistry("imageModel")
    private final ImageModel imageModel
            = prompt -> new ImageResponse(List.of(new ImageGeneration(new Image(URL, null))));

    @Test
    public void base64ImageConvertsToTheDecodedBytes() throws Exception {
        Image image = new Image(null, "aGVsbG8=");

        assertThat(context.getTypeConverter().convertTo(byte[].class, image))
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        try (InputStream is = context.getTypeConverter().convertTo(InputStream.class, image)) {
            assertThat(is.readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(context.getTypeConverter().convertTo(String.class, image)).isEqualTo("aGVsbG8=");
    }

    @Test
    public void urlOnlyImageFailsToConvertToBytes() {
        Image image = new Image(URL, null);

        assertThatThrownBy(() -> context.getTypeConverter().convertTo(byte[].class, image))
                .isInstanceOf(TypeConversionException.class)
                .rootCause()
                .hasMessageContaining("responseFormat=b64_json");
        assertThatThrownBy(() -> context.getTypeConverter().convertTo(InputStream.class, image))
                .isInstanceOf(TypeConversionException.class)
                .rootCause()
                .hasMessageContaining("responseFormat=b64_json");
        // the text of the image is still its URL
        assertThat(context.getTypeConverter().convertTo(String.class, image)).isEqualTo(URL);
    }

    @Test
    public void urlOnlyImageIsNotWrittenToFile() {
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("A camel"));

        assertThat(out.getException()).isNotNull();
        assertThat(out.getException()).rootCause().hasMessageContaining("responseFormat=b64_json");
        assertThat(tempDir.resolve("image.png")).doesNotExist();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("spring-ai-image:gen?imageModel=#imageModel")
                        .to("file:" + tempDir + "?fileName=image.png");
            }
        };
    }
}
