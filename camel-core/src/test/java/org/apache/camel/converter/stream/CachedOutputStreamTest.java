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
package org.apache.camel.converter.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.IOHelper;

public class CachedOutputStreamTest extends ContextTestSupport {
    private static final String TEST_STRING = "This is a test string and it has enough" 
        + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";

    private Exchange exchange;

    protected void setUp() throws Exception {
        super.setUp();
        
        context.getProperties().put(CachedOutputStream.TEMP_DIR, "target/cachedir");
        context.getProperties().put(CachedOutputStream.THRESHOLD, "16");
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");

        exchange = new DefaultExchange(context);
        UnitOfWork uow = new DefaultUnitOfWork(exchange);
        exchange.setUnitOfWork(uow);
    }

    private static String toString(InputStream input) throws IOException {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(input));
        CollectionStringBuffer builder = new CollectionStringBuffer();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.append(line);
        }
    }

    public void testCacheStreamToFileAndCloseStream() throws IOException {       
        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes("UTF-8"));

        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals("we should have a temp file", files.length, 1);
        assertTrue("The file name should start with cos" , files[0].startsWith("cos"));
        
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the FileInputStreamCache", cache instanceof FileInputStreamCache);
        String temp = toString((InputStream)cache);

        ((InputStream)cache).close();
        assertEquals("we should have a temp file", files.length, 1);
        assertEquals("Cached a wrong file", temp, TEST_STRING);
        exchange.getUnitOfWork().done(exchange);

        try {
            cache.reset();
            // The stream is closed, so the temp file is gone.
            fail("we expect the exception here");
        } catch (Exception exception) {
            // do nothing
        }


        files = file.list();
        assertEquals("we should have no temp file", files.length, 0);
    }
    
    public void testCacheStreamToFileCloseStreamBeforeDone() throws IOException {
        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes("UTF-8"));

        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals("we should have a temp file", files.length, 1);
        assertTrue("The file name should start with cos" , files[0].startsWith("cos"));
        
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the FileInputStreamCache", cache instanceof FileInputStreamCache);
        String temp = toString((InputStream)cache);
        assertEquals("Cached a wrong file", temp, TEST_STRING);
        cache.reset();
        temp = toString((InputStream)cache);
        assertEquals("Cached a wrong file", temp, TEST_STRING);        
        exchange.getUnitOfWork().done(exchange);
        assertEquals("we should have a temp file", files.length, 1);
        ((InputStream)cache).close();
        
        files = file.list();
        assertEquals("we should have no temp file", files.length, 0);       
    }
    
    public void testCacheStreamToMemory() throws IOException {
        context.getProperties().put(CachedOutputStream.THRESHOLD, "1024");

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes("UTF-8"));

        File file = new File("target/cachedir");
        String[] files = file.list();

        assertEquals("we should have no temp file", files.length, 0);
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the InputStreamCache", cache instanceof InputStreamCache);
        String temp = IOConverter.toString((InputStream)cache, null);
        assertEquals("Cached a wrong file", temp, TEST_STRING);
    }

    public void testCacheStreamToMemoryAsDiskIsdisabled() throws IOException {
        // -1 disables disk based cache
        context.getProperties().put(CachedOutputStream.THRESHOLD, "-1");

        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes("UTF-8"));

        File file = new File("target/cachedir");
        String[] files = file.list();

        assertEquals("we should have no temp file", files.length, 0);
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the InputStreamCache", cache instanceof InputStreamCache);
        String temp = IOConverter.toString((InputStream)cache, null);
        assertEquals("Cached a wrong file", temp, TEST_STRING);

        exchange.getUnitOfWork().done(exchange);
    }
    
    public void testCachedOutputStreamCustomBufferSize() throws IOException {
        // double the default buffer size
        context.getProperties().put(CachedOutputStream.BUFFER_SIZE, "4096");
        
        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes("UTF-8"));

        assertEquals("we should have a custom buffer size", cos.getBufferSize(), 4096);
        
        // make sure things still work after custom buffer size set
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertEquals("we should have a temp file", files.length, 1);
        assertTrue("The file name should start with cos" , files[0].startsWith("cos"));              
        
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the FileInputStreamCache", cache instanceof FileInputStreamCache);
        String temp = toString((InputStream)cache);
        assertEquals("Cached a wrong file", temp, TEST_STRING);
        cache.reset();
        temp = toString((InputStream)cache);
        assertEquals("Cached a wrong file", temp, TEST_STRING);        
        exchange.getUnitOfWork().done(exchange);
        assertEquals("we should have a temp file", files.length, 1);
        ((InputStream)cache).close();
        
        files = file.list();
        assertEquals("we should have no temp file", files.length, 0);       
    }
}
