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
package org.apache.camel.impl.console;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConsoleHelperTest {

    @Test
    public void testExtractSourceLocationLineNumber() {
        Integer lineNumber = ConsoleHelper.extractSourceLocationLineNumber("file.java:42");
        Assertions.assertEquals(42, lineNumber);
    }

    @Test
    public void testExtractSourceLocationLineNumberNoNumber() {
        Integer lineNumber = ConsoleHelper.extractSourceLocationLineNumber("file.java");
        Assertions.assertNull(lineNumber);
    }

    @Test
    public void testExtractSourceLocationLineNumberMultipleColons() {
        Integer lineNumber = ConsoleHelper.extractSourceLocationLineNumber("classpath:com/example/file.java:100");
        Assertions.assertEquals(100, lineNumber);
    }

    @Test
    public void testExtractSourceLocationLineNumberInvalid() {
        Integer lineNumber = ConsoleHelper.extractSourceLocationLineNumber("file.java:abc");
        Assertions.assertNull(lineNumber);
    }

    @Test
    public void testLoadSourceAsJsonFromReader() {
        String source = "line one\nline two\nline three";
        StringReader reader = new StringReader(source);

        List<JsonObject> result = ConsoleHelper.loadSourceAsJson(reader, 2);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());

        JsonObject line1 = result.get(0);
        Assertions.assertEquals(1, line1.getInteger("line"));
        Assertions.assertEquals("line one", line1.getString("code"));
        Assertions.assertNull(line1.get("match"));

        JsonObject line2 = result.get(1);
        Assertions.assertEquals(2, line2.getInteger("line"));
        Assertions.assertEquals("line two", line2.getString("code"));
        Assertions.assertTrue(line2.getBoolean("match"));

        JsonObject line3 = result.get(2);
        Assertions.assertEquals(3, line3.getInteger("line"));
        Assertions.assertEquals("line three", line3.getString("code"));
        Assertions.assertNull(line3.get("match"));
    }

    @Test
    public void testLoadSourceAsJsonFromReaderNoLineNumber() {
        String source = "line one\nline two";
        StringReader reader = new StringReader(source);

        List<JsonObject> result = ConsoleHelper.loadSourceAsJson(reader, null);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());

        // No match should be set when lineNumber is null
        for (JsonObject jo : result) {
            Assertions.assertNull(jo.get("match"));
        }
    }

    @Test
    public void testLoadSourceAsJsonFromReaderEmpty() {
        StringReader reader = new StringReader("");

        List<JsonObject> result = ConsoleHelper.loadSourceAsJson(reader, 1);
        Assertions.assertNull(result);
    }

    @Test
    public void testLoadSourceAsJsonNullReader() {
        Reader nullReader = null;
        List<JsonObject> result = ConsoleHelper.loadSourceAsJson(nullReader, 1);
        Assertions.assertNull(result);
    }
}
