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
package org.apache.camel.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Message;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GZIPHelperTest {

    private static byte[] sampleBytes = new byte[]{1, 2, 3, 1, 2, 3};
    private static String sampleString = "<Hello>World</Hello>";

    @Test
    public void toGZIPInputStreamShouldReturnTheSameInputStream() throws IOException {
        InputStream inputStream = GZIPHelper.uncompressGzip("text", new ByteArrayInputStream(sampleBytes));
        byte[] bytes = new byte[6];
        inputStream.read(bytes);

        assertEquals(-1, inputStream.read());
        assertArrayEquals(sampleBytes, bytes);
    }

    @Test
    public void toGZIPInputStreamShouldReturnAByteArrayInputStream() throws IOException {
        InputStream inputStream = GZIPHelper.compressGzip("text", sampleBytes);

        byte[] bytes = IOConverter.toBytes(inputStream);
        assertArrayEquals(sampleBytes, bytes);
    }
    
    @Test
    public void testCompressAndUnCompressData() throws IOException {
        InputStream inputStream = GZIPHelper.compressGzip("gzip", new ByteArrayInputStream(sampleString.getBytes()));
        assertNotNull("The inputStream should not be null.", inputStream);
        inputStream = GZIPHelper.uncompressGzip("gzip", inputStream);        
        String result = IOConverter.toString(inputStream, null);
        assertEquals("The result is wrong.", sampleString, result);
        
    }

    @Test
    public void testIsGzipMessage() {
        assertTrue(GZIPHelper.isGzip(createMessageWithContentEncodingHeader("gzip")));
        assertTrue(GZIPHelper.isGzip(createMessageWithContentEncodingHeader("GZip")));

        assertFalse(GZIPHelper.isGzip(createMessageWithContentEncodingHeader(null)));
        assertFalse(GZIPHelper.isGzip(createMessageWithContentEncodingHeader("zip")));
    }

    @Test
    public void isGzipString() {
        assertTrue(GZIPHelper.isGzip("gzip"));
        assertTrue(GZIPHelper.isGzip("GZip"));

        assertFalse(GZIPHelper.isGzip((String) null));
        assertFalse(GZIPHelper.isGzip("zip"));
    }

    private Message createMessageWithContentEncodingHeader(String contentEncoding) {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setHeader("Content-Encoding", contentEncoding);

        return msg;
    }
}