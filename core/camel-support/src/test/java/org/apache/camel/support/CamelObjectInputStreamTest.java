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
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelObjectInputStreamTest {

    @Test
    void defaultFilterAllowsStandardTypes() throws Exception {
        assertEquals("hello camel", deserialize(serialize("hello camel")));
    }

    @Test
    void defaultFilterRejectsClassOutsideAllowList() throws Exception {
        byte[] data = serialize(InetSocketAddress.createUnresolved("example.com", 8080));
        InvalidClassException ex = assertThrows(InvalidClassException.class, () -> deserialize(data));
        assertTrue(ex.getMessage().contains("REJECTED"), ex.getMessage());
    }

    @Test
    void blankFilterFallsBackToDefault() throws Exception {
        byte[] data = serialize(InetSocketAddress.createUnresolved("example.com", 8080));
        InvalidClassException ex = assertThrows(InvalidClassException.class, () -> deserialize(data, "   "));
        assertTrue(ex.getMessage().contains("REJECTED"), ex.getMessage());
    }

    @Test
    void explicitFilterCanAllowOtherwiseDeniedClass() throws Exception {
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 8080);
        assertEquals(address, deserialize(serialize(address), "java.**;!*"));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
        }
        return bos.toByteArray();
    }

    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (CamelObjectInputStream ois = new CamelObjectInputStream(new ByteArrayInputStream(data), null)) {
            return ois.readObject();
        }
    }

    private static Object deserialize(byte[] data, String filter) throws IOException, ClassNotFoundException {
        try (CamelObjectInputStream ois = new CamelObjectInputStream(new ByteArrayInputStream(data), null, filter)) {
            return ois.readObject();
        }
    }
}
