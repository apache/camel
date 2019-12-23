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
package org.apache.camel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.camel.ContextTestSupport;
import org.junit.Test;

public class IOHelperCharsetTest extends ContextTestSupport {

    private static final String CONTENT = "G\u00f6tzend\u00e4mmerung,Joseph und seine Br\u00fcder";

    @Test
    public void testToInputStreamFileWithCharsetUTF8() throws Exception {
        switchToDefaultCharset(StandardCharsets.UTF_8);
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        try (InputStream in = IOHelper.toInputStream(file, "UTF-8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            BufferedReader naiveReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        }
    }

    @Test
    public void testToInputStreamFileWithCharsetUTF8withOtherDefaultEncoding() throws Exception {
        switchToDefaultCharset(StandardCharsets.ISO_8859_1);
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        try (InputStream in = IOHelper.toInputStream(file, "UTF-8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            BufferedReader naiveReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        }
    }

    @Test
    public void testToInputStreamFileWithCharsetLatin1() throws Exception {
        switchToDefaultCharset(StandardCharsets.UTF_8);
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        try (InputStream in = IOHelper.toInputStream(file, "ISO-8859-1");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            BufferedReader naiveReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.ISO_8859_1))) {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        }
    }

    @Test
    public void testToInputStreamFileDirectByteDumpWithCharsetLatin1() throws Exception {
        switchToDefaultCharset(StandardCharsets.UTF_8);
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        try (InputStream in = IOHelper.toInputStream(file, "ISO-8859-1"); InputStream naiveIn = Files.newInputStream(Paths.get(file.getAbsolutePath()))) {
            byte[] bytes = new byte[8192];
            in.read(bytes);
            byte[] naiveBytes = new byte[8192];
            naiveIn.read(naiveBytes);
            assertFalse("both input streams deliver the same byte sequence", Arrays.equals(naiveBytes, bytes));
        }
    }

    @Test
    public void testToReaderFileWithCharsetUTF8() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
        try (BufferedReader reader = IOHelper.toReader(file, "UTF-8");
            BufferedReader naiveReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        }
    }

    @Test
    public void testToReaderFileWithCharsetLatin1() throws Exception {
        File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
        try (BufferedReader reader = IOHelper.toReader(file, "ISO-8859-1");
            BufferedReader naiveReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.ISO_8859_1))) {
            String line = reader.readLine();
            String naiveLine = naiveReader.readLine();
            assertEquals(naiveLine, line);
            assertEquals(CONTENT, line);
        }
    }

    private static void switchToDefaultCharset(final Charset charset) {
        IOHelper.defaultCharset = () -> charset;
    }
}
