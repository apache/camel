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
package org.apache.camel.util.json;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class JsonSimpleOrderedTest extends Assert {

    @Test
    public void testOrdered() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/bean.json");
        String json = loadText(is);
        JsonObject output = Jsoner.deserialize(json, new JsonObject());

        assertNotNull(output);

        // should preserve order
        Map map = output.getMap("component");
        assertTrue(map instanceof LinkedHashMap);
        Iterator it = map.keySet().iterator();
        assertEquals("kind", it.next());
        assertEquals("scheme", it.next());
        assertEquals("syntax", it.next());
        assertEquals("title", it.next());
        assertEquals("description", it.next());
        assertEquals("label", it.next());
        assertEquals("deprecated", it.next());
        assertEquals("deprecationNote", it.next());
        assertEquals("async", it.next());
        assertEquals("consumerOnly", it.next());
        assertEquals("producerOnly", it.next());
        assertEquals("lenientProperties", it.next());
        assertEquals("javaType", it.next());
        assertEquals("firstVersion", it.next());
        assertEquals("groupId", it.next());
        assertEquals("artifactId", it.next());
        assertEquals("version", it.next());
        assertFalse(it.hasNext());
    }

    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);

        try {
            BufferedReader reader = new BufferedReader(isr);

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    line = builder.toString();
                    return line;
                }

                builder.append(line);
                builder.append("\n");
            }
        } finally {
            isr.close();
            in.close();
        }
    }

}
