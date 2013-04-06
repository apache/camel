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
package org.apache.camel.component.mina2;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import junit.framework.TestCase;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.mina.core.buffer.IoBuffer;

/**
 * @version 
 */
public class Mina2ConverterTest extends TestCase {

    public void testToByteArray() {
        byte[] in = "Hello World".getBytes();
        IoBuffer bb = IoBuffer.wrap(in);

        byte[] out = Mina2Converter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }

    public void testToString() throws UnsupportedEncodingException {
        String in = "Hello World \u4f60\u597d";
        IoBuffer bb = IoBuffer.wrap(in.getBytes("UTF-8"));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String out = Mina2Converter.toString(bb, exchange);
        assertEquals("Hello World \u4f60\u597d", out);
    }

    public void testToStringTwoTimes() throws UnsupportedEncodingException {
        String in = "Hello World \u4f60\u597d";
        IoBuffer bb = IoBuffer.wrap(in.getBytes("UTF-8"));
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");

        String out = Mina2Converter.toString(bb, exchange);
        assertEquals("Hello World \u4f60\u597d", out);

        // should NOT be possible to convert to string without affecting the ByteBuffer
        out = Mina2Converter.toString(bb, exchange);
        assertEquals("", out);
    }

    public void testToInputStream() throws Exception {
        byte[] in = "Hello World".getBytes();
        IoBuffer bb = IoBuffer.wrap(in);

        InputStream is = Mina2Converter.toInputStream(bb);
        for (byte b : in) {
            int out = is.read();
            assertEquals(b, out);
        }
    }

    public void testToByteBuffer() {
        byte[] in = "Hello World".getBytes();

        IoBuffer bb = Mina2Converter.toIoBuffer(in);
        assertNotNull(bb);

        // convert back to byte[] and see if the bytes are equal
        bb.flip(); // must flip to change direction to read
        byte[] out = Mina2Converter.toByteArray(bb);

        for (int i = 0; i < out.length; i++) {
            assertEquals(in[i], out[i]);
        }
    }
}
