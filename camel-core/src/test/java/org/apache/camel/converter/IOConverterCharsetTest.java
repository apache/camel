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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.camel.ContextTestSupport;

public class IOConverterCharsetTest extends ContextTestSupport {
    private static final String CONTENT = "G\u00f6tzend\u00e4mmerung,Joseph und seine Br\u00fcder";
    private static final Charset DEFAULT_CHARSET = Charset.defaultCharset();
    
    // Just set the default charset back
    protected void tearDown() throws Exception {
        super.tearDown();
        switchToDefaultCharset(DEFAULT_CHARSET.displayName());
    }

    public void testToInputStreamFileWithCharsetUTF8() throws Exception {
        switchToDefaultCharset("UTF-8");
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        InputStream in = IOConverter.toInputStream(file, "UTF-8");
        // do read with default charset!
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {   
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        } finally {
            reader.close();
            naiveReader.close();
        }
        
    }

    public void testToInputStreamFileWithCharsetUTF8withOtherDefaultEncoding() throws Exception {
        switchToDefaultCharset("ISO-8859-1");
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        InputStream in = IOConverter.toInputStream(file, "UTF-8");
        // do read with default charset!
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {   
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        } finally {
            reader.close();
            naiveReader.close();
        }
        
    }

    public void testToInputStreamFileWithCharsetLatin1() throws Exception {
        switchToDefaultCharset("UTF-8");
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        InputStream in = IOConverter.toInputStream(file, "ISO-8859-1");
        // do read with default charset!
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
        try {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        } finally {
            reader.close();
            naiveReader.close();
        }
    }

    public void testToInputStreamFileDirectByteDumpWithCharsetLatin1() throws Exception {
        switchToDefaultCharset("UTF-8");
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        InputStream in = IOConverter.toInputStream(file, "ISO-8859-1");
        InputStream naiveIn = new FileInputStream(file);
        try {
            byte[] bytes = new byte[8192];
            in.read(bytes);
            byte[] naiveBytes = new byte[8192];
            naiveIn.read(naiveBytes);
            assertFalse("both input streams deliver the same byte sequence", Arrays.equals(naiveBytes, bytes));
        } finally {
            in.close();
            naiveIn.close();
        }
    }

    public void testToReaderFileWithCharsetUTF8() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        BufferedReader reader = IOConverter.toReader(file, "UTF-8");
        BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        } finally {
            reader.close();
            naiveReader.close();
        }
    }

    public void testToReaderFileWithCharsetLatin1() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        BufferedReader reader = IOConverter.toReader(file, "ISO-8859-1");
        BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
        try {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        } finally {
            reader.close();
            naiveReader.close();
        }
    }


    private void switchToDefaultCharset(String charset) {
        try {
            Field defaultCharset = Charset.class.getDeclaredField("defaultCharset");
            defaultCharset.setAccessible(true);
            defaultCharset.set(null, Charset.forName(charset));
        } catch (Exception e) {
            // Do nothing here
        }
    }
}
