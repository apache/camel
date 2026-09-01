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
package org.apache.camel.component.ai.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scenario from the issue: a read-only retrieval of a static file from a remote store.
 */
public class AiResourceStaticFileTest extends CamelTestSupport {

    private static final Path DIR = Path.of("target", "resource-scenario");
    private static final byte[] PDF = { 0x25, 0x50, 0x44, 0x46, 0x2d, 0x31 };

    @BeforeAll
    static void writeFiles() throws Exception {
        Files.createDirectories(DIR);
        Files.writeString(DIR.resolve("app.json"), "{\"env\":\"prod\"}");
        Files.write(DIR.resolve("report.pdf"), PDF);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // stands in for ftp:; same GenericFile polling consumer family
                from("ai-resource:app_config?resourceUri=file:///config/app.json&tags=test"
                     + "&mimeType=application/json")
                        .pollEnrich("file:" + DIR + "?fileName=app.json&noop=true&idempotent=false", 5000);

                from("ai-resource:report?resourceUri=file:///reports/report.pdf&tags=test"
                     + "&mimeType=application/pdf")
                        .pollEnrich("file:" + DIR + "?fileName=report.pdf&noop=true&idempotent=false", 5000);

                // stands in for aws2-s3 getObject, which yields a stream body
                from("ai-resource:s3_object?resourceUri=s3://reports/latest.pdf&tags=test"
                     + "&mimeType=application/pdf")
                        .process(e -> e.getMessage().setBody(new ByteArrayInputStream(PDF), InputStream.class));
            }
        };
    }

    @Test
    public void testStaticTextFile() {
        AiResourceResult result = read("file:///config/app.json");
        assertThat(result).isInstanceOf(AiResourceResult.Text.class);
        assertThat(((AiResourceResult.Text) result).value()).isEqualTo("{\"env\":\"prod\"}");
    }

    @Test
    public void testStaticBinaryFile() {
        AiResourceResult result = read("file:///reports/report.pdf");
        assertThat(result).isInstanceOf(AiResourceResult.Binary.class);
        assertThat(((AiResourceResult.Binary) result).value()).isEqualTo(PDF);
    }

    @Test
    public void testStreamBodyFromObjectStore() {
        AiResourceResult result = read("s3://reports/latest.pdf");
        assertThat(result).isInstanceOf(AiResourceResult.Binary.class);
        assertThat(((AiResourceResult.Binary) result).value()).isEqualTo(PDF);
    }

    @Test
    public void testRepeatedReadsReturnTheSameContent() throws Exception {
        String first = ((AiResourceResult.Text) read("file:///config/app.json")).value();
        String second = ((AiResourceResult.Text) read("file:///config/app.json")).value();
        assertThat(second).isEqualTo(first).isEqualTo("{\"env\":\"prod\"}");
        assertThat(new String(Files.readAllBytes(DIR.resolve("app.json")), StandardCharsets.UTF_8))
                .as("noop=true must leave the file in place")
                .isEqualTo(first);
    }

    private AiResourceResult read(String resourceUri) {
        AiResourceSpec spec = AiResourceRegistry.getOrCreate(context).getResourcesByTag("test").stream()
                .filter(s -> resourceUri.equals(s.getUri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Resource not found: " + resourceUri));
        Exchange exchange = new DefaultExchange(context);
        return AiResourceExecutor.execute(spec, exchange);
    }
}
