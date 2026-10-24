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
package org.apache.camel.component.stream;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.stream.StreamGroupLinesTest.LS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25974: a file name without a directory is written, and stream:header without a stream header fails with an
 * error that names the header.
 */
public class StreamProducerErrorsTest extends CamelTestSupport {

    // a file name without a directory, so the file is in the current directory
    private final String fileName = "StreamProducerErrorsTest-" + UUID.randomUUID() + ".txt";

    @AfterEach
    public void deleteFile() throws Exception {
        Files.deleteIfExists(new File(fileName).toPath());
    }

    @Test
    public void testFileNameWithoutDirectory() throws Exception {
        Exchange out = template.send("direct:file", e -> e.getIn().setBody("Hello"));

        assertNull(out.getException());
        assertEquals("Hello" + LS, Files.readString(new File(fileName).toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void testHeaderMissing() {
        Exchange out = template.send("direct:header", e -> e.getIn().setBody("Hello"));

        NoSuchHeaderException cause = assertInstanceOf(NoSuchHeaderException.class, out.getException());
        assertEquals("stream", cause.getHeaderName());
    }

    @Test
    public void testHeaderNotOutputStream() {
        Exchange out = template.send("direct:header", e -> {
            e.getIn().setHeader("stream", 123);
            e.getIn().setBody("Hello");
        });

        NoSuchHeaderException cause = assertInstanceOf(NoSuchHeaderException.class, out.getException());
        assertEquals("stream", cause.getHeaderName());
        assertTrue(cause.getMessage().contains("java.io.OutputStream"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:file").to("stream:file?fileName=" + fileName);
                from("direct:header").to("stream:header");
            }
        };
    }
}
