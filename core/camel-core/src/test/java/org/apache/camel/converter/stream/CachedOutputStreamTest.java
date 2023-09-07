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
package org.apache.camel.converter.stream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CachedOutputStreamTest extends ContextTestSupport {
    private static final String TEST_STRING = "This is a test string and it has enough"
                                              + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";

    private Exchange exchange;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setStreamCaching(true);
        context.getStreamCachingStrategy().setSpoolDirectory(testDirectory(true).toFile());
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        context.getStreamCachingStrategy().setSpoolThreshold(16);
        return context;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        exchange = new DefaultExchange(context);
        UnitOfWork uow = new DefaultUnitOfWork(exchange);
        exchange.getExchangeExtension().setUnitOfWork(uow);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static String toString(InputStream input) throws IOException {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(input));
        StringJoiner builder = new StringJoiner(", ");
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.add(line);
        }
    }

    @Test
    public void testCachedStreamAccessStreamWhenExchangeOnCompletion() throws Exception {
        context.start();
        CachedOutputStream cos = new CachedOutputStream(exchange, false);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        File file = testDirectory().toFile();
        String[] files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertTrue(files[0].startsWith("cos"), "The file name should start with cos");

        InputStream is = cos.getWrappedInputStream();
        exchange.getUnitOfWork().done(exchange);
        String temp = toString(is);
        assertEquals(TEST_STRING, temp, "Get a wrong stream content");
        IOHelper.close(is);

        files = file.list();
        assertEquals(0, files.length, "we should have a temp file");
        IOHelper.close(cos);
    }

    @Test
    public void testCacheStreamToFileAndCloseStream() throws Exception {
        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        File file = testDirectory().toFile();
        String[] files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertTrue(files[0].startsWith("cos"), "The file name should start with cos");

        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof FileInputStreamCache;
        assertTrue(b, "Should get the FileInputStreamCache");
        String temp = toString((InputStream) cache);

        ((InputStream) cache).close();
        files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertEquals(TEST_STRING, temp, "Cached a wrong file");
        exchange.getUnitOfWork().done(exchange);

        try {
            cache.reset();
            // The stream is closed, so the temp file is gone.
            fail("we expect the exception here");
        } catch (Exception exception) {
            // do nothing
        }

        files = file.list();
        assertEquals(0, files.length, "we should have no temp file");

        IOHelper.close(cos);
    }

    @Test
    public void testCacheStreamToFileAndCloseStreamEncrypted() throws Exception {
        // set some stream or 8-bit block cipher transformation name
        context.getStreamCachingStrategy().setSpoolCipher("RC4");

        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));
        cos.flush();

        File file = testDirectory().toFile();
        String[] files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertTrue(new File(file, files[0]).length() > 10, "The content is written");

        java.io.FileInputStream tmpin = new java.io.FileInputStream(new File(file, files[0]));
        String temp = toString(tmpin);
        assertTrue(temp.length() > 0 && !temp.contains("aaa"), "The content is not encrypted");
        tmpin.close();

        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof FileInputStreamCache;
        assertTrue(b, "Should get the FileInputStreamCache");
        temp = toString((InputStream) cache);

        ((InputStream) cache).close();
        assertEquals(1, files.length, "we should have a temp file");
        assertEquals(TEST_STRING, temp, "Cached a wrong file");
        exchange.getUnitOfWork().done(exchange);

        assertThrows(Exception.class, cache::reset, "We expect the exception here");

        files = file.list();
        assertEquals(0, files.length, "we should have no temp file");

        IOHelper.close(cos);
    }

    @Test
    public void testCacheStreamToFileCloseStreamBeforeDone() throws Exception {
        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        File file = testDirectory().toFile();
        String[] files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertTrue(files[0].startsWith("cos"), "The file name should start with cos");

        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof FileInputStreamCache;
        assertTrue(b, "Should get the FileInputStreamCache");
        String temp = toString((InputStream) cache);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");
        cache.reset();
        temp = toString((InputStream) cache);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");
        ((InputStream) cache).close();
        files = file.list();
        assertEquals(1, files.length, "we should have a temp file");

        exchange.getUnitOfWork().done(exchange);
        files = file.list();
        assertEquals(0, files.length, "we should have no temp file");

        IOHelper.close(cos);
    }

    @Test
    public void testCacheStreamToMemory() throws Exception {
        context.getStreamCachingStrategy().setSpoolThreshold(1024);

        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        File file = testDirectory().toFile();
        String[] files = file.list();

        assertEquals(0, files.length, "we should have no temp file");
        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof InputStreamCache;
        assertTrue(b, "Should get the InputStreamCache");
        String temp = IOConverter.toString((InputStream) cache, null);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");

        IOHelper.close(cos);
    }

    @Test
    public void testCacheStreamToMemoryAsDiskIsDisabled() throws Exception {
        // -1 disables disk based cache
        context.getStreamCachingStrategy().setSpoolThreshold(-1);

        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        File file = testDirectory().toFile();
        String[] files = file.list();

        assertEquals(0, files.length, "we should have no temp file");
        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof InputStreamCache;
        assertTrue(b, "Should get the InputStreamCache");
        String temp = IOConverter.toString((InputStream) cache, null);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");

        exchange.getUnitOfWork().done(exchange);

        IOHelper.close(cos);
    }

    @Test
    public void testCachedOutputStreamCustomBufferSize() throws Exception {
        // double the default buffer size
        context.getStreamCachingStrategy().setBufferSize(8192);

        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        assertEquals(8192, cos.getStrategyBufferSize(), "we should have a custom buffer size");

        // make sure things still work after custom buffer size set
        File file = testDirectory().toFile();
        String[] files = file.list();
        assertEquals(1, files.length, "we should have a temp file");
        assertTrue(files[0].startsWith("cos"), "The file name should start with cos");

        StreamCache cache = cos.newStreamCache();
        boolean b = cache instanceof FileInputStreamCache;
        assertTrue(b, "Should get the FileInputStreamCache");
        String temp = toString((InputStream) cache);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");
        cache.reset();
        temp = toString((InputStream) cache);
        assertEquals(TEST_STRING, temp, "Cached a wrong file");

        ((InputStream) cache).close();
        files = file.list();
        assertEquals(1, files.length, "we should have a temp file");

        exchange.getUnitOfWork().done(exchange);
        files = file.list();
        assertEquals(0, files.length, "we should have no temp file");

        IOHelper.close(cos);
    }

    @Test
    public void testCachedOutputStreamEmptyInput() throws Exception {
        context.start();

        CachedOutputStream cos = new CachedOutputStream(exchange, false);
        // write an empty string
        cos.write("".getBytes(StandardCharsets.UTF_8));
        InputStream is = cos.getWrappedInputStream();
        assertNotNull(is);

        // copy to output stream
        ByteArrayOutputStream bos = new ByteArrayOutputStream(16);
        IOHelper.copy(is, bos);
        assertNotNull(bos);
        byte[] data = bos.toByteArray();
        assertEquals(0, data.length);

        IOHelper.close(bos);
        IOHelper.close(cos);
    }

}
