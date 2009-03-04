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

import junit.framework.TestCase;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.CollectionStringBuffer;

public class CachedOutputStreamTest extends TestCase {
    private static final String TEST_STRING = "This is a test string and it has enough" 
        + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";
    
    private File file = new File("./target/cacheFile");

    private static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteDirectory(files[i]);
            }
        }
        file.delete();
    }
    
    private static String toString(InputStream input) throws IOException {        
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        CollectionStringBuffer builder = new CollectionStringBuffer("\n");
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.append(line);
        }
    }
    
    protected void setUp() throws Exception {        
        if (file.exists()) {
            deleteDirectory(file);
        }
        file.mkdirs();
    }
       
    public void testCacheStreamToFileAndCloseStream() throws IOException {       
        
        CachedOutputStream cos = new CachedOutputStream(16);
        cos.setOutputDir(file);
        cos.write(TEST_STRING.getBytes("UTF-8"));        
        String[] files = file.list();
        assertEquals("we should have a temp file", files.length, 1);
        assertTrue("The file name should start with cos" , files[0].startsWith("cos"));
        
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the FileInputStreamCache", cache instanceof FileInputStreamCache);
        String temp = toString((InputStream)cache);
        ((InputStream)cache).close();
        assertEquals("Cached a wrong file", temp, TEST_STRING);
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
    
    public void testCacheStreamToFileAndNotCloseStream() throws IOException {       
        
        CachedOutputStream cos = new CachedOutputStream(16);
        cos.setOutputDir(file);
        cos.write(TEST_STRING.getBytes("UTF-8"));        
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
        
        ((InputStream)cache).close();
        files = file.list();
        assertEquals("we should have no temp file", files.length, 0);       
    }
    
    public void testCacheStreamToMemory() throws IOException {
        CachedOutputStream cos = new CachedOutputStream();
        cos.setOutputDir(file);
        cos.write(TEST_STRING.getBytes("UTF-8"));        
        String[] files = file.list();
        assertEquals("we should have no temp file", files.length, 0);
        StreamCache cache = cos.getStreamCache();
        assertTrue("Should get the InputStreamCache", cache instanceof InputStreamCache);
        String temp = IOConverter.toString((InputStream)cache);
        assertEquals("Cached a wrong file", temp, TEST_STRING);
    }
}
