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
package org.apache.camel.converter;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version 
 */
public class NIOConverterTest extends ContextTestSupport {

    public void testToByteArray() {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());
        byte[] out = NIOConverter.toByteArray(bb);
        assertNotNull(out);
        assertEquals(5, out.length);
    }

    public void testToString() throws Exception {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());
        String out = NIOConverter.toString(bb, null);
        assertNotNull(out);
        assertEquals("Hello", out);
    }

    public void testToByteBuffer() {
        ByteBuffer bb = NIOConverter.toByteBuffer("Hello".getBytes());
        assertNotNull(bb);
    }

    public void testToByteBufferString() {
        ByteBuffer bb = NIOConverter.toByteBuffer("Hello", null);
        assertNotNull(bb);
    }

    public void testToByteBufferFile() throws Exception {
        template.sendBodyAndHeader("file://target/nio", "Hello World", Exchange.FILE_NAME, "hello.txt");

        ByteBuffer bb = NIOConverter.toByteBuffer(new File("target/nio/hello.txt"));
        assertNotNull(bb);

        assertEquals("Hello World", NIOConverter.toString(bb, null));
    }

    public void testToByteBufferShort() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Short.valueOf("2"));
        assertNotNull(bb);

        bb.position(0);
        assertEquals(2, bb.getShort());
    }

    public void testToByteBufferInteger() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Integer.valueOf("2"));
        assertNotNull(bb);

        bb.position(0);
        assertEquals(2, bb.getInt());
    }

    public void testToByteBufferLong() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Long.valueOf("2"));
        assertNotNull(bb);

        bb.position(0);
        assertEquals(2, bb.getLong());
    }

    public void testToByteBufferDouble() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Double.valueOf("2"));
        assertNotNull(bb);

        bb.position(0);
        assertEquals(2.0d, bb.getDouble());
    }

    public void testToByteBufferFloat() {
        ByteBuffer bb = NIOConverter.toByteBuffer(Float.valueOf("2"));
        assertNotNull(bb);

        bb.position(0);
        assertEquals(2.0f, bb.getFloat());
    }

    public void testToInputStream() throws Exception {
        ByteBuffer bb = ByteBuffer.wrap("Hello".getBytes());

        InputStream is = NIOConverter.toInputStream(bb);
        assertNotNull(is);

        assertEquals("Hello", IOConverter.toString(is, null));
    }

}
