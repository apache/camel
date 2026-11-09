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
package org.apache.camel.component.xj;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.exc.StreamReadException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The output and the failures that xj had with Jackson 2.
 */
class XJJackson3BehaviourTest extends CamelTestSupport {

    private static final String XML = "<root><b>a/b 😀</b></root>";
    private static final String JSON_ESCAPED = "{\"b\":\"a/b \\uD83D\\uDE00\"}";
    private static final String JSON_RAW = "{\"b\":\"a/b 😀\"}";

    @TempDir
    Path tempDir;

    @Test
    void stringOutputKeepsSurrogatePair() {
        assertThat(template.requestBody("direct:x2jString", XML, String.class)).isEqualTo(JSON_RAW);
    }

    @Test
    void bytesOutputEscapesSurrogatePair() {
        byte[] out = template.requestBody("direct:x2jBytes", XML, byte[].class);
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo(JSON_ESCAPED);
    }

    @Test
    void fileOutputEscapesSurrogatePair() throws Exception {
        Path file = tempDir.resolve("out.json");
        template.requestBodyAndHeader("direct:x2jFile", XML, Exchange.XSLT_FILE_NAME, file.toString());
        assertThat(Files.readString(file)).isEqualTo(JSON_ESCAPED);
    }

    @Test
    void unwritableFileIsAnIOException() {
        String file = tempDir.resolve("missing/out.json").toString();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:x2jFile", XML, Exchange.XSLT_FILE_NAME, file));
        assertThat(e).cause().isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void unreadableFileIsAnIOException() {
        File file = tempDir.resolve("missing.json").toFile();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:j2x", file));
        assertThat(e).cause().isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void invalidJsonFailsWithXmlStreamException() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:j2x", "{\"a\":tru}"));
        assertThat(e).hasRootCauseInstanceOf(StreamReadException.class);
        assertThat(e).cause().cause().isInstanceOf(XMLStreamException.class);
    }

    @Test
    void invalidJsonTextFailsWithUncheckedIOException() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:j2x", "{\"a\":\"unterminated"));
        assertThat(e).cause().isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void contentAfterTheFirstValueIsIgnored() {
        String out = template.requestBody("direct:j2x", "{\"a\":1} x", String.class);
        assertThat(out).contains("xj:name=\"a\"");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:x2jString").to("xj:identity?transformDirection=XML2JSON");
                from("direct:x2jBytes").to("xj:identity?transformDirection=XML2JSON&output=bytes");
                from("direct:x2jFile").to("xj:identity?transformDirection=XML2JSON&output=file");
                from("direct:j2x").to("xj:identity?transformDirection=JSON2XML");
            }
        };
    }
}
