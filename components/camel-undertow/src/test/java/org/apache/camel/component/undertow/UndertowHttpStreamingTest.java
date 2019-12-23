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
package org.apache.camel.component.undertow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class UndertowHttpStreamingTest extends BaseUndertowTest {

    private static final String LINE =
            String.join("", Collections.nCopies(100, "0123456789"));
    private static final long COUNT = 1000; // approx. 1MB

    @Test
    public void testTwoWayStreaming() throws Exception {
        long expectedLength = LINE.length() * COUNT;
        MockEndpoint mock = getMockEndpoint("mock:length");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedLength);

        Exchange response = template.send(
        "undertow:http://localhost:{{port}}?useStreaming=true",
        e -> produceStream(e));
        consumeStream(response);
        long length = response.getIn().getBody(Long.class).longValue();

        mock.assertIsSatisfied();
        assertEquals(expectedLength, length);
    }

    @Test
    public void testOneWayStreaming() throws Exception {
        long expectedLength = LINE.length() * COUNT;
        MockEndpoint mock = getMockEndpoint("mock:length");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(12);

        Exchange response = template.send(
        "undertow:http://localhost:{{port}}?useStreaming=true",
        e -> e.getIn().setBody("Hello Camel!"));
        consumeStream(response);
        long length = response.getIn().getBody(Long.class).longValue();

        mock.assertIsSatisfied();
        assertEquals(expectedLength, length);
    }

    private static void produceStream(Exchange exchange) throws IOException {
        PipedOutputStream out = new PipedOutputStream();
        exchange.getIn().setBody(new PipedInputStream(out));
        new Thread(() -> {
            try (OutputStreamWriter osw = new OutputStreamWriter(out);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                LongStream.range(0, COUNT).forEach(i -> {
                    try {
                        writer.write(LINE);
                        writer.newLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void consumeStream(Exchange exchange) throws IOException {
        try (InputStream in = exchange.getIn().getBody(InputStream.class);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            long length = reader.lines()
                    .collect(Collectors.summingLong(String::length));
            exchange.getIn().setBody(length);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}?useStreaming=true")
                        .process(e -> consumeStream(e))
                        .to("mock:length")
                        .process(e -> produceStream(e));
            }
        };
    }

}
