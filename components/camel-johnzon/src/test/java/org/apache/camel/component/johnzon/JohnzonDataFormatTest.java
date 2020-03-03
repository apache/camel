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
package org.apache.camel.component.johnzon;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.johnzon.mapper.reflection.JohnzonParameterizedType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JohnzonDataFormatTest {
    
    @Test
    public void testString() throws Exception {
        testJson("\"A string\"", "A string", String.class, null);
    }

    @Test
    public void testMap() throws Exception {
        testJson("{\"value\":123}", Collections.singletonMap("value", 123), Map.class, null);
    }

    @Test
    public void testList() throws Exception {
        JohnzonParameterizedType type = new JohnzonParameterizedType(List.class, Map.class);
        testJson("[{\"value\":123}]",
            new ArrayList<>(Collections.singletonList(Collections.singletonMap("value", 123))), null, type);
    }

    @Test
    public void testArray() throws Exception {
        testJson("{\"value\":123}", new ArrayList<String>(), ArrayList.class, null);
    }

    @Test
    public void testSkipEmptyArray() throws Exception {
        JohnzonParameterizedType type = new JohnzonParameterizedType(ArrayList.class, ArrayList.class);
        testJson("[{\"value\":123}]",
            new ArrayList<>(Collections.singletonList(Collections.emptyList())), null, type);
    }

    private void testJson(String json, Object expected, Class<?> unmarshalType, JohnzonParameterizedType parameterizedType) throws Exception {
        Object unmarshalled;
        JohnzonDataFormat johnzonDataFormat = null;

        try {
            if (unmarshalType != null) {
                johnzonDataFormat = new JohnzonDataFormat(unmarshalType);
            } else {
                johnzonDataFormat = new JohnzonDataFormat(parameterizedType);
            }
            johnzonDataFormat.setSkipEmptyArray(true);
            johnzonDataFormat.doStart();
            try (InputStream in = new ByteArrayInputStream(json.getBytes())) {
                unmarshalled = johnzonDataFormat.unmarshal(new DefaultExchange(new DefaultCamelContext()), in);
            }
            assertEquals(expected, unmarshalled);
        } finally {
            if (johnzonDataFormat != null) {
                johnzonDataFormat.close();
            }
        }
    }
}
