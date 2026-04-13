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
package org.apache.camel.component.mina;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import com.example.external.NotAllowedSerializable;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MinaConverterTest {

    @Test
    public void testToByteArray() {
        byte[] in = "Hello World".getBytes();
        IoBuffer bb = IoBuffer.wrap(in);

        byte[] out = MinaConverter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

    @Test
    public void testToString() throws UnsupportedEncodingException {
        String in = "Hello World \u4f60\u597d";
        IoBuffer bb = IoBuffer.wrap(in.getBytes(StandardCharsets.UTF_8));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String out = MinaConverter.toString(bb, exchange);
        assertEquals("Hello World \u4f60\u597d", out);
    }

    @Test
    public void testToStringTwoTimes() throws UnsupportedEncodingException {
        String in = "Hello World \u4f60\u597d";
        IoBuffer bb = IoBuffer.wrap(in.getBytes(StandardCharsets.UTF_8));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String out = MinaConverter.toString(bb, exchange);
        assertEquals("Hello World \u4f60\u597d", out);

        // should NOT be possible to convert to string without affecting the ByteBuffer
        out = MinaConverter.toString(bb, exchange);
        assertEquals("", out);
    }

    @Test
    public void testToInputStream() throws Exception {
        byte[] in = "Hello World".getBytes();
        IoBuffer bb = IoBuffer.wrap(in);

        try (InputStream is = MinaConverter.toInputStream(bb)) {
            for (byte b : in) {
                int out = is.read();
                assertEquals(b, out);
            }
        }
    }

    @Test
    public void testToByteBuffer() {
        byte[] in = "Hello World".getBytes();

        IoBuffer bb = MinaConverter.toIoBuffer(in);
        assertNotNull(bb);

        // convert back to byte[] and see if the bytes are equal
        bb.flip(); // must flip to change direction to read
        byte[] out = MinaConverter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

    @Test
    public void testToObjectInputAcceptsAllowlistedTypes() throws Exception {
        IoBuffer bb = serialize("hello");
        try (ObjectInput in = MinaConverter.toObjectInput(bb)) {
            Object value = in.readObject();
            assertInstanceOf(String.class, value);
            assertEquals("hello", value);
        }
    }

    @Test
    public void testToObjectInputRejectsUnlistedTypes() throws Exception {
        IoBuffer bb = serialize(new NotAllowedSerializable("blocked"));
        try (ObjectInput in = MinaConverter.toObjectInput(bb)) {
            assertThrows(InvalidClassException.class, in::readObject);
        }
    }

    private static IoBuffer serialize(Object value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return IoBuffer.wrap(baos.toByteArray());
    }
}
