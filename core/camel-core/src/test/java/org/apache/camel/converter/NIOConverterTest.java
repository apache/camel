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
package org.apache.camel.converter;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Test;

public class NIOConverterTest extends ContextTestSupport {

    @Test
    public void testToByteArray() {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());
        byte[] out = NIOConverter.toByteArray(bb);
        assertNotNull(out);
        assertEquals(5, out.length);
    }

    /**
     * Test if returned array size is only to limit of ByteBuffer. If byteBuffer
     * capacity is bigger that limit, we MUST return data only to the limit.
     */
    @Test
    public void testToByteArrayBigBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(100);
        bb.put("Hello".getBytes());
        bb.flip();
        byte[] out = NIOConverter.toByteArray(bb);
        assertNotNull(out);
        assertEquals(5, out.length);
    }

    @Test
    public void testToString() throws Exception {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());
        String out = NIOConverter.toString(bb, null);
        assertNotNull(out);
        assertEquals("Hello", out);
    }

    /**
     * ToString need to deal the array size issue as ToByteArray does
     */
    @Test
    public void testByteBufferToStringConversion() throws Exception {
        String str = "123456789";
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.put(str.getBytes());
        buffer.flip();

        String out = NIOConverter.toString(buffer, null);
        assertEquals(str, out);
    }

    @Test
    public void testToByteBuffer() {
        ByteBuffer bb = NIOConverter.toByteBuffer("Hello".getBytes());
        assertNotNull(bb);
    }

    @Test
    public void testToByteBufferString() {
        ByteBuffer bb = NIOConverter.toByteBuffer("Hello", null);
        assertNotNull(bb);
    }

    @Test
    public void testToByteBufferFile() throws Exception {
        template.sendBodyAndHeader("file://target/data/nio", "Hello World", Exchange.FILE_NAME, "hello.txt");

        ByteBuffer bb = NIOConverter.toByteBuffer(new File("target/data/nio/hello.txt"));
        assertNotNull(bb);

        assertEquals("Hello World", NIOConverter.toString(bb, null));
    }

    @Test
    public void testToByteBufferShort() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Short.valueOf("2"));
        assertNotNull(bb);
        assertEquals(2, bb.getShort());
    }

    @Test
    public void testToByteBufferInteger() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Integer.valueOf("2"));
        assertNotNull(bb);
        assertEquals(2, bb.getInt());
    }

    @Test
    public void testToByteBufferLong() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Long.valueOf("2"));
        assertNotNull(bb);
        assertEquals(2, bb.getLong());
    }

    @Test
    public void testToByteBufferDouble() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Double.valueOf("2"));
        assertNotNull(bb);
        assertEquals(2.0d, bb.getDouble(), 1e-5d);
    }

    @Test
    public void testToByteBufferFloat() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Float.valueOf("2"));
        assertNotNull(bb);
        assertEquals(2.0f, bb.getFloat(), 1e-5f);
    }

    @Test
    public void testToInputStream() throws Exception {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());

        InputStream is = NIOConverter.toInputStream(bb);
        assertNotNull(is);

        assertEquals("Hello", IOConverter.toString(is, null));
    }

}
