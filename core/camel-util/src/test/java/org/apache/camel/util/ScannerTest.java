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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScannerTest {

    @Test
    public void testScannerString() throws IOException {
        String d = "data1\ndata2\ndata3\n";

        try (Scanner s = new Scanner(d, "\n")) {
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data1", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data2", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data3", s.next());
            Assertions.assertFalse(s.hasNext());
        }
    }

    @Test
    public void testScannerInputStream() throws IOException {
        String d = "data1\ndata2\ndata3\n";
        InputStream is = new ByteArrayInputStream(d.getBytes(StandardCharsets.UTF_8));

        try (Scanner s = new Scanner(is, "UTF-8", "\n")) {
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data1", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data2", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data3", s.next());
            Assertions.assertFalse(s.hasNext());
        }
    }

    @Test
    public void testPipedInputStream() throws Exception {
        PipedOutputStream pos = new PipedOutputStream();
        InputStream is = new PipedInputStream(pos);

        pos.write("data1\n".getBytes());
        pos.write("data2\n".getBytes());
        pos.write("data3\n".getBytes());
        pos.flush();

        try (Scanner s = new Scanner(is, "UTF-8", "\n")) {
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data1", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data2", s.next());
            Assertions.assertTrue(s.hasNext());
            Assertions.assertEquals("data3", s.next());
        }
    }

}
