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
package org.apache.camel.component.stax;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The StAX producer accepts every body that can be read as XML: text, bytes, a stream, a file, a path, a
 * {@link javax.xml.transform.Source} and a DOM {@link Document}.
 */
public class StAXBodyTypesTest extends CamelTestSupport {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                      + "<records><record key=\"1\">a</record><record key=\"2\">æøå</record></records>";

    @TempDir
    Path tempDir;

    @Override
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:count").to("stax:" + CountingHandler.class.getName());
                from("direct:text").to("stax:" + TextHandler.class.getName());
            }
        };
    }

    @Test
    public void testString() {
        assertCount(XML);
    }

    @Test
    public void testBytes() {
        assertCount(XML.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testInputStream() {
        assertCount(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testFile() throws Exception {
        assertCount(writeFile().toFile());
    }

    @Test
    public void testPath() throws Exception {
        assertCount(writeFile());
    }

    @Test
    public void testSource() {
        assertCount(new StreamSource(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void testDocument() {
        Document document = context.getTypeConverter().convertTo(Document.class, XML);
        assertCount(document);
    }

    @Test
    public void testBytesKeepNonAsciiText() {
        TextHandler handler
                = template.requestBody("direct:text", XML.getBytes(StandardCharsets.UTF_8), TextHandler.class);
        assertEquals("aæøå", handler.getText());
    }

    @Test
    public void testBytesDoNotReadExternalEntities() throws Exception {
        File secret = tempDir.resolve("secret.txt").toFile();
        Files.writeString(secret.toPath(), "top-secret");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE records [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>"
                     + "<records>&xxe;</records>";

        // the bytes are parsed by the same secured factory as text: the external entity is not resolved
        TextHandler handler = template.requestBody("direct:text", xml.getBytes(StandardCharsets.UTF_8), TextHandler.class);
        assertFalse(handler.getText().contains("top-secret"));
    }

    @Test
    public void testMapFails() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:count", Map.of("records", "a")));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    private Path writeFile() throws Exception {
        Path file = tempDir.resolve("records.xml");
        Files.writeString(file, XML, StandardCharsets.UTF_8);
        return file;
    }

    private void assertCount(Object body) {
        CountingHandler handler = template.requestBody("direct:count", body, CountingHandler.class);
        assertEquals(3, handler.getNumber());
    }

    public static class TextHandler extends DefaultHandler {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            text.append(ch, start, length);
        }

        public String getText() {
            return text.toString();
        }
    }
}
