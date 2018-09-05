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
package org.apache.camel.component.jackson;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JacksonDataFormatTest {

    @Test
    public void testString() throws Exception {
        testJson("\"A string\"", "A string");
    }

    @Test
    public void testMap() throws Exception {
        testJson("{\"value\":123}", Collections.singletonMap("value", 123));
    }

    @Test
    public void testList() throws Exception {
        testJson("[{\"value\":123}]", new ArrayList<>(Collections.singletonList(Collections.singletonMap("value", 123))));
    }

    private void testJson(String json, Object expected) throws Exception {
        Object unmarshalled;
        JacksonDataFormat gsonDataFormat = new JacksonDataFormat();
        gsonDataFormat.doStart();
        try (InputStream in = new ByteArrayInputStream(json.getBytes())) {
            unmarshalled = gsonDataFormat.unmarshal(new DefaultExchange(new DefaultCamelContext()), in);
        }

        assertEquals(expected, unmarshalled);
    }
}