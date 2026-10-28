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
package org.apache.camel.dataformat.deflater;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The gzip body is a stream, which stream caching (on by default) keeps in memory so that it can be read again after
 * the unmarshal: by a dead letter channel that uses the original message, or by a later step of the route.
 */
class GzipDataFormatBodyReadAgainTest extends CamelTestSupport {

    @Test
    void deadLetterChannelGetsTheWholeOriginalBody() throws Exception {
        byte[] gzip = gzip("Hello World".getBytes(StandardCharsets.UTF_8));
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:failing", new ByteArrayInputStream(gzip));

        dead.assertIsSatisfied();
        assertArrayEquals(gzip, dead.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    void deadLetterChannelGetsTheWholeOriginalBodyOfALargeMessage() throws Exception {
        // random bytes do not compress: the gzip body is larger than the read buffer of the gzip reader
        byte[] data = new byte[20000];
        new Random(42).nextBytes(data);
        byte[] gzip = gzip(data);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:failing", new ByteArrayInputStream(gzip));

        dead.assertIsSatisfied();
        assertArrayEquals(gzip, dead.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    @Test
    void theCompressedBodyKeptInAPropertyCanBeReadAfterTheUnmarshal() throws Exception {
        byte[] gzip = gzip("Hello World".getBytes(StandardCharsets.UTF_8));
        MockEndpoint archive = getMockEndpoint("mock:archive");
        archive.expectedMessageCount(1);

        // not a ByteArrayInputStream, so the stream is copied into the stream cache
        template.sendBody("direct:archive", new InputStream() {
            private final InputStream delegate = new ByteArrayInputStream(gzip);

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return delegate.read(b, off, len);
            }
        });

        archive.assertIsSatisfied();
        assertArrayEquals(gzip, archive.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:failing")
                        .errorHandler(deadLetterChannel("direct:dead").useOriginalMessage())
                        .unmarshal().gzipDeflater()
                        .throwException(new IllegalStateException("Forced"));

                from("direct:dead")
                        .convertBodyTo(byte[].class)
                        .to("mock:dead");

                from("direct:archive")
                        .setProperty("compressed", body())
                        .unmarshal().gzipDeflater()
                        .setBody(exchangeProperty("compressed"))
                        .convertBodyTo(byte[].class)
                        .to("mock:archive");
            }
        };
    }
}
