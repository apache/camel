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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.example.external.NotAllowedSerializable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeserializationFilterHelperTest {

    @Test
    public void testStreamDefaultContainsGraphShapeLimits() {
        String filter = DeserializationFilterHelper.DEFAULT_DESERIALIZATION_FILTER;
        assertTrue(filter.contains("maxdepth="), "Expected maxdepth in filter: " + filter);
        assertTrue(filter.contains("maxrefs="), "Expected maxrefs in filter: " + filter);
        assertTrue(filter.contains("maxbytes="), "Expected maxbytes in filter: " + filter);
    }

    @Test
    public void testClassCheckDefaultOmitsGraphShapeLimits() {
        String filter = DeserializationFilterHelper.DEFAULT_CLASS_DESERIALIZATION_FILTER;
        assertFalse(filter.contains("maxdepth="), "Graph limits cannot fire in class-check mode: " + filter);
        assertFalse(filter.contains("maxrefs="), "Graph limits cannot fire in class-check mode: " + filter);
        assertFalse(filter.contains("maxbytes="), "Graph limits cannot fire in class-check mode: " + filter);
    }

    @Test
    public void testDefaultFilterAllowsStandardAndCamelTypes() throws Exception {
        assertEquals("hello", deserialize(serialize("hello"), null));
        assertInstanceOf(HashMap.class, deserialize(serialize(new HashMap<String, String>()), null));
        assertInstanceOf(DefaultExchangeHolder.class, deserialize(serialize(new DefaultExchangeHolder()), null));
    }

    @Test
    public void testDefaultFilterRejectsJavaNetClass() throws Exception {
        byte[] data = serialize(URI.create("http://example.com/"));
        assertThrows(InvalidClassException.class, () -> deserialize(data, null));
    }

    @Test
    public void testDefaultFilterRejectsUnlistedType() throws Exception {
        byte[] data = serialize(new NotAllowedSerializable("blocked"));
        assertThrows(InvalidClassException.class, () -> deserialize(data, null));
    }

    @Test
    public void testConfiguredFilterWinsOverDefault() throws Exception {
        byte[] data = serialize(new NotAllowedSerializable("allowed"));
        Object value = deserialize(data, "com.example.external.*;java.**;!*");
        assertInstanceOf(NotAllowedSerializable.class, value);
        assertEquals("allowed", ((NotAllowedSerializable) value).getValue());
    }

    @Test
    public void testBlankPatternIsTreatedAsAbsent() throws Exception {
        byte[] data = serialize(new NotAllowedSerializable("blocked"));
        assertThrows(InvalidClassException.class, () -> deserialize(data, "  "));
    }

    @Test
    public void testDefaultFilterEnforcesDepthLimitOnStreams() throws Exception {
        Object graph = "leaf";
        for (int i = 0; i < 30; i++) {
            List<Object> wrapper = new ArrayList<>();
            wrapper.add(graph);
            graph = wrapper;
        }
        byte[] data = serialize(graph);
        assertThrows(InvalidClassException.class, () -> deserialize(data, null));
    }

    @Test
    public void testCheckClassAllowsStandardAndCamelTypes() {
        ObjectInputFilter filter = DeserializationFilterHelper.resolveDeserializationFilter(
                null, DeserializationFilterHelper.DEFAULT_CLASS_DESERIALIZATION_FILTER);
        assertNotEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, HashMap.class));
        assertNotEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, DefaultExchangeHolder.class));
    }

    @Test
    public void testCheckClassRejectsJavaNetAndUnlistedTypes() {
        ObjectInputFilter filter = DeserializationFilterHelper.resolveDeserializationFilter(
                null, DeserializationFilterHelper.DEFAULT_CLASS_DESERIALIZATION_FILTER);
        assertEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, URI.class));
        assertEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, NotAllowedSerializable.class));
    }

    @Test
    public void testCheckClassHonorsConfiguredPattern() {
        ObjectInputFilter filter = DeserializationFilterHelper.resolveDeserializationFilter(
                "com.example.external.*;!*", DeserializationFilterHelper.DEFAULT_CLASS_DESERIALIZATION_FILTER);
        assertNotEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, NotAllowedSerializable.class));
        assertEquals(ObjectInputFilter.Status.REJECTED,
                DeserializationFilterHelper.checkClass(filter, HashMap.class));
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return baos.toByteArray();
    }

    private static Object deserialize(byte[] data, String configuredPattern) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            ois.setObjectInputFilter(DeserializationFilterHelper.resolveDeserializationFilter(configuredPattern));
            return ois.readObject();
        }
    }
}
