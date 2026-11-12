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
package org.apache.camel.component.jms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jakarta.jms.MessageEOFException;
import jakarta.jms.MessageFormatException;
import jakarta.jms.StreamMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamMessageInputStreamTest {

    private static final byte[] PAYLOAD = "Hello World from a StreamMessage".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testReadAllBytesOverSeveralFields() throws IOException {
        // readAllBytes reads into the array at an offset after the first read
        InputStream is = new StreamMessageInputStream(streamMessage(PAYLOAD, 5));

        assertArrayEquals(PAYLOAD, is.readAllBytes());
        assertEquals(-1, is.read());
    }

    @Test
    public void testReadOneByteAtATime() throws IOException {
        // the fields are byte[] fields, which cannot be read with readByte
        InputStream is = new StreamMessageInputStream(streamMessage(PAYLOAD, 7));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = is.read()) != -1) {
            out.write(b);
        }
        assertArrayEquals(PAYLOAD, out.toByteArray());
    }

    @Test
    public void testReadWithBufferSmallerThanField() throws IOException {
        InputStream is = new StreamMessageInputStream(streamMessage(PAYLOAD, 20));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[3];
        int num;
        while ((num = is.read(buffer)) != -1) {
            out.write(buffer, 0, num);
        }
        assertArrayEquals(PAYLOAD, out.toByteArray());
    }

    @Test
    public void testReadTextWithMultiByteCharactersAcrossFields() throws IOException {
        String text = "Grüße aus Århus, 東京からこんにちは ".repeat(500);
        InputStream is = new StreamMessageInputStream(streamMessage(text.getBytes(StandardCharsets.UTF_8), 1001));

        StringWriter out = new StringWriter();
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            reader.transferTo(out);
        }
        assertEquals(text, out.toString());
    }

    @Test
    public void testResetReadsAgain() throws IOException {
        InputStream is = new StreamMessageInputStream(streamMessage(PAYLOAD, 9));

        assertArrayEquals(PAYLOAD, is.readAllBytes());
        is.reset();
        assertArrayEquals(PAYLOAD, is.readAllBytes());
    }

    /**
     * A StreamMessage holding the payload as byte[] fields of the given size, as the producer writes it, which follows
     * the readBytes contract of the JMS API.
     */
    private static StreamMessage streamMessage(byte[] payload, int fieldSize) {
        int count = (payload.length + fieldSize - 1) / fieldSize;
        byte[][] fields = new byte[count][];
        for (int i = 0; i < count; i++) {
            int from = i * fieldSize;
            fields[i] = Arrays.copyOfRange(payload, from, Math.min(payload.length, from + fieldSize));
        }
        // the current field, and the position in it (-1 when its reading has not started)
        int[] state = { 0, -1 };
        return (StreamMessage) Proxy.newProxyInstance(StreamMessageInputStreamTest.class.getClassLoader(),
                new Class<?>[] { StreamMessage.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "readBytes": {
                            byte[] target = (byte[]) args[0];
                            if (state[0] >= fields.length) {
                                throw new MessageEOFException("No more fields");
                            }
                            byte[] field = fields[state[0]];
                            if (state[1] == field.length) {
                                // the field was read completely with a full buffer: -1, then the next field
                                state[0]++;
                                state[1] = -1;
                                return -1;
                            }
                            int pos = Math.max(state[1], 0);
                            int num = Math.min(field.length - pos, target.length);
                            System.arraycopy(field, pos, target, 0, num);
                            state[1] = pos + num;
                            if (num < target.length) {
                                // fewer bytes than the buffer: the field is complete
                                state[0]++;
                                state[1] = -1;
                            }
                            return num;
                        }
                        case "readByte":
                            throw new MessageFormatException("A byte[] field cannot be read as a byte");
                        case "reset":
                            state[0] = 0;
                            state[1] = -1;
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
