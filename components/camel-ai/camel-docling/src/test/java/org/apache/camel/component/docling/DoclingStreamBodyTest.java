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
package org.apache.camel.component.docling;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A stream body (an InputStream, or a stream cache that stream caching made of one) is written to the temp file the
 * docling CLI reads, as a byte[] body is, within maxFileSize; a stream cache is reset after it was read.
 */
@DisabledOnOs(OS.WINDOWS)
class DoclingStreamBodyTest extends CamelTestSupport {

    private static final String DOCUMENT = "# Hello";

    @TempDir
    Path tempDir;

    @Test
    void inputStreamBody() {
        InputStream body = new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8));

        assertThat(template.requestBody("direct:no-cache", body, String.class)).isEqualTo(DOCUMENT);
    }

    @Test
    void streamCacheBodyIsResetAfterReading() throws Exception {
        InputStreamCache body = new InputStreamCache(DOCUMENT.getBytes(StandardCharsets.UTF_8));

        assertThat(template.requestBody("direct:convert", body, String.class)).isEqualTo(DOCUMENT);
        assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(DOCUMENT);
    }

    @Test
    void streamConvertedFromStringWithStreamCaching() {
        // as in a route that does convertBodyTo(InputStream.class): stream caching makes a stream cache of it
        assertThat(template.requestBody("direct:convert-stream", DOCUMENT, String.class)).isEqualTo(DOCUMENT);
    }

    @Test
    void streamLargerThanMaxFileSizeIsRejected() {
        InputStream body = new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> template.requestBody("direct:small-no-cache", body))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed size: 4");
    }

    @Test
    void streamCacheLargerThanMaxFileSizeIsRejected() {
        InputStreamCache body = new InputStreamCache(DOCUMENT.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> template.requestBody("direct:small", body))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed size: 4");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // a stand-in for the docling CLI: copies the input (the last argument) as markdown into the
                // directory given by --output
                Path docling = tempDir.resolve("docling");
                Files.writeString(docling, """
                        #!/bin/sh
                        out=""
                        prev=""
                        for arg in "$@"; do
                          if [ "$prev" = "--output" ]; then out="$arg"; fi
                          prev="$arg"
                        done
                        name=$(basename "$prev")
                        cp "$prev" "$out/${name%.*}.md"
                        """);
                Files.setPosixFilePermissions(docling, PosixFilePermissions.fromString("rwx------"));

                from("direct:convert")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&doclingCommand=" + docling);

                // stream caching is on by default; without it the body stays the plain InputStream that was sent
                from("direct:no-cache").streamCache("false")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&doclingCommand=" + docling);

                from("direct:convert-stream").streamCache("true")
                        .convertBodyTo(InputStream.class)
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&doclingCommand=" + docling);

                from("direct:small")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&maxFileSize=4&doclingCommand=" + docling);

                from("direct:small-no-cache").streamCache("false")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&maxFileSize=4&doclingCommand=" + docling);
            }
        };
    }
}
