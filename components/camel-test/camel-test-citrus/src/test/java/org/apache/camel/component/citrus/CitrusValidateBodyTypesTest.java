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
package org.apache.camel.component.citrus;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.citrusframework.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bodies that are not a String or a stream cache: they are read as text (or as bytes for the binary message types), and
 * a plain stream is not consumed by the validation.
 */
class CitrusValidateBodyTypesTest extends CamelTestSupport {

    private static final String JSON = "{\"status\": \"ok\"}";

    @TempDir
    Path tempDir;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // without stream caching a plain InputStream reaches the citrus endpoint as is
        context.setStreamCaching(false);
        return context;
    }

    @Test
    void shouldValidateJsonByteArrayBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body(JSON);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:json", JSON.getBytes(StandardCharsets.UTF_8));

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldFailOnJsonByteArrayBodyMismatch() {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body(JSON);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:json", "{\"status\": \"failed\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(AssertionError.class)
                .hasCauseInstanceOf(ValidationException.class);
    }

    @Test
    void shouldValidatePlainTextByteArrayBody() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/text?messageType=plaintext", CitrusEndpoint.class);
        citrus.validateAll().body("Hello Camel!");
        citrus.expectedMessageCount(1);

        template.sendBody("direct:text", "Hello Camel!".getBytes(StandardCharsets.UTF_8));

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldValidateFileBodyByContent() throws Exception {
        File file = Files.writeString(tempDir.resolve("status.json"), JSON).toFile();

        CitrusEndpoint citrus = context.getEndpoint("citrus:validate?messageType=json", CitrusEndpoint.class);
        citrus.validateAll().body(JSON);
        citrus.expectedMessageCount(1);

        template.sendBody("direct:json", file);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldValidatePathBodyByContent() throws Exception {
        Path path = Files.writeString(tempDir.resolve("hello.txt"), "Hello Camel!");

        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/text?messageType=plaintext", CitrusEndpoint.class);
        citrus.validateAll().body("Hello Camel!");
        citrus.expectedMessageCount(1);

        template.sendBody("direct:text", path);

        citrus.assertIsSatisfied();
    }

    @Test
    void shouldNotConsumePlainInputStream() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/stream?messageType=plaintext", CitrusEndpoint.class);
        citrus.validateAll().body("Hello Camel!");
        citrus.expectedMessageCount(1);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello Camel!");

        InputStream body = new ByteArrayInputStream("Hello Camel!".getBytes(StandardCharsets.UTF_8));
        template.sendBody("direct:stream", body);

        citrus.assertIsSatisfied();
        result.assertIsSatisfied();
        assertThat(result.getReceivedExchanges().get(0).getIn().getBody()).isInstanceOf(StreamCache.class);
    }

    @Test
    void shouldNotConsumePlainReader() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/stream?messageType=plaintext", CitrusEndpoint.class);
        citrus.validateAll().body("Hello Camel!");
        citrus.expectedMessageCount(1);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello Camel!");

        template.sendBody("direct:stream", new StringReader("Hello Camel!"));

        citrus.assertIsSatisfied();
        result.assertIsSatisfied();
    }

    @Test
    void shouldValidateStreamCacheAsBytesForBinaryBase64() throws Exception {
        byte[] data = new byte[] { 0, 1, 2, (byte) 0xCA, (byte) 0xFE };

        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/binary?messageType=binary_base64", CitrusEndpoint.class);
        citrus.validateAll().body(Base64.getEncoder().encodeToString(data));
        citrus.expectedMessageCount(1);
        MockEndpoint result = getMockEndpoint("mock:binary");
        result.expectedMessageCount(1);

        template.sendBody("direct:binary", new InputStreamCache(data));

        citrus.assertIsSatisfied();
        result.assertIsSatisfied();
        assertThat(result.getReceivedExchanges().get(0).getIn().getBody(byte[].class)).isEqualTo(data);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:json").to("citrus:validate?messageType=json");
                from("direct:text").to("citrus:validate/text?messageType=plaintext");
                from("direct:stream").to("citrus:validate/stream?messageType=plaintext").to("mock:result");
                from("direct:binary").to("citrus:validate/binary?messageType=binary_base64").to("mock:binary");
            }
        };
    }
}
