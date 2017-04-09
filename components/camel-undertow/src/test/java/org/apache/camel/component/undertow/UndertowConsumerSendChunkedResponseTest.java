/**
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class UndertowConsumerSendChunkedResponseTest extends BaseUndertowTest {

    private final class DummyInputStream extends InputStream {
        int c;

        @Override
        public int read() throws IOException {
            if (c < TRANSFER_SIZE) {
                return Math.abs(c++) % 255;
            } else {
                return -1;
            }
        }
    }

    static final int BUFFER_SIZE = 8192;

    static final int TRANSFER_SIZE = BUFFER_SIZE * 2 + BUFFER_SIZE / 2;

    private byte[] expected;

    @Before
    public void calculateExpectedDigest() throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA1");

        int read = -1;
        byte[] buffy = new byte[64 * 1024];

        try (InputStream in = new DummyInputStream()) {
            while ((read = in.read(buffy)) != -1) {
                digest.update(buffy, 0, read);
            }
        }

        expected = digest.digest();
    }

    @Test
    public void shouldTransferInChunks() throws IOException, NoSuchAlgorithmException {
        int chunkLength = 0;
        int sumChunkLength = 0;

        MessageDigest received = MessageDigest.getInstance("SHA1");

        try (Socket socket = new Socket("localhost", getPort());
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream()) {
            out.write("GET /chunked HTTP/1.1\r\n\r\n".getBytes());

            int read = -1;
            while ((read = in.read()) != -1) {
                if (read == '\r' && in.read() == '\n' && in.read() == '\r' && in.read() == '\n') {
                    // we have reached end of headers
                    break;
                }
            }

            int data;
            while ((data = in.read()) != -1) {
                if (Character.isDigit(data)) {
                    chunkLength = chunkLength * 16 + Character.digit(data, 16);
                    // still reading chunk length
                    continue;
                } else {
                    sumChunkLength += chunkLength;
                }

                if (chunkLength == 0) {
                    break;
                }

                // read the '\n', '\r' was read by the while loop condition and it did not pass isDigit test
                assertEquals("The last byte between two chunks should be `\\n`", '\n', in.read());

                byte[] buffy = new byte[chunkLength];
                chunkLength = 0;

                read = in.read(buffy);
                assertEquals("Should have read the chunk size", '\r', chunkLength, read);

                received.update(buffy);

                assertEquals("The second to last byte at the end of the chunk should be `\\r`", '\r', in.read());
                assertEquals("The last byte at the end of the chunk should be `\\n`", '\n', in.read());
            }
        }

        assertEquals("Sum of all chunks should be the transfered length", TRANSFER_SIZE, sumChunkLength);
        assertEquals("The very last chunk should be 0-length", 0, chunkLength);
        assertArrayEquals("The digest of transfered bytes should be as expected", expected, received.digest());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}/chunked?chunked=true").process(new Processor() {
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        exchange.getIn().setBody(new BufferedInputStream(new DummyInputStream(), BUFFER_SIZE));
                    }
                });
            }
        };
    }

}
