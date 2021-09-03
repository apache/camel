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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SkipLastNewlineInputStreamTest {

    @Test
    void read() throws IOException {

        assertRead("foo bar\n", "foo bar");
        assertRead("foo bar\n\n", "foo bar\n");
        assertRead("foo\nbar\n", "foo\nbar");
        assertRead("foo\n\nbar\n", "foo\n\nbar");
        assertRead("", "");
        assertRead("foo bar", "foo bar");
        assertRead("\n", "");
        assertRead("f\n", "f");
        assertRead("fo\n", "fo");
        assertRead("foo\n", "foo");
        assertRead("foo \n", "foo ");

    }

    private void assertRead(String input, String expected) throws IOException {

        try (InputStream in
                = new SkipLastByteInputStream(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), (byte) '\n');
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int c;
            while ((c = in.read()) >= 0) {
                out.write(c);
            }
            Assertions.assertEquals(expected, new String(out.toByteArray(), StandardCharsets.UTF_8));
        }

        try (InputStream in
                = new SkipLastByteInputStream(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), (byte) '\n');
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[3];
            int len;
            while ((len = in.read(buf)) >= 0) {
                out.write(buf, 0, len);
            }
            Assertions.assertEquals(expected, new String(out.toByteArray(), StandardCharsets.UTF_8));
        }

    }

}
