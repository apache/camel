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
package org.apache.camel.dataformat.base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unmarshal of a body that is not in memory: the input stream is closed when unmarshal returns, so the data must be
 * decoded by then (CAMEL-25995).
 */
public class Base64DataFormatFileTest extends CamelTestSupport {

    private static final String ENCODED = "SGVsbG8gQ2FtZWwgZnJvbSBhIGZpbGU=";
    private static final String DECODED = "Hello Camel from a file";

    @TempDir
    Path tempDir;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    void testUnmarshalFile() throws Exception {
        File file = writeFile();

        result.expectedBodiesReceived(DECODED);

        template.sendBody("direct:decode", file);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testUnmarshalFileStreamWithoutStreamCaching() throws Exception {
        File file = writeFile();

        result.expectedBodiesReceived(DECODED);

        try (InputStream is = new FileInputStream(file)) {
            template.sendBody("direct:decodeNoStreamCache", is);
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    private File writeFile() throws Exception {
        Path file = tempDir.resolve("data.b64");
        Files.writeString(file, ENCODED, StandardCharsets.US_ASCII);
        return file.toFile();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:decode").unmarshal().base64().convertBodyTo(String.class).to("mock:result");

                from("direct:decodeNoStreamCache").streamCache(false)
                        .unmarshal().base64().convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
