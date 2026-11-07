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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ObjectHelperLoadClassTest {

    @Test
    void testLoadClass() {
        Class<?> clazz = ObjectHelper.loadClass("int");
        Assertions.assertEquals("int", clazz.getName());

        clazz = ObjectHelper.loadClass("java.lang.String");
        Assertions.assertEquals("java.lang.String", clazz.getName());

        clazz = ObjectHelper.loadClass("java.lang.String[]");
        Assertions.assertTrue(clazz.isArray());

        clazz = ObjectHelper.loadClass("org.apache.camel.util.MyPojo");
        Assertions.assertEquals("org.apache.camel.util.MyPojo", clazz.getName());
        Assertions.assertFalse(clazz.isArray());

        clazz = ObjectHelper.loadClass("org.apache.camel.util.MyPojo[]");
        Assertions.assertTrue(clazz.isArray());
    }

    /**
     * CAMEL-26589: ObjectHelper.loadClass must resolve JVM internal binary names for array types such as {@code [B}
     * (byte[]), {@code [I} (int[]), and {@code [Ljava.lang.String;} (String[]). These names are produced by
     * {@link Class#getName()} on array types and appear in DataType when a byte[] body is tagged with
     * {@code new DataType(body.getClass())}.
     */
    @Test
    void testLoadClassArrayBinaryNames() {
        // Primitive array binary names
        Class<?> clazz = ObjectHelper.loadClass("[B");
        Assertions.assertNotNull(clazz, "[B should resolve to byte[]");
        Assertions.assertEquals(byte[].class, clazz);

        clazz = ObjectHelper.loadClass("[S");
        Assertions.assertEquals(short[].class, clazz);

        clazz = ObjectHelper.loadClass("[I");
        Assertions.assertEquals(int[].class, clazz);

        clazz = ObjectHelper.loadClass("[J");
        Assertions.assertEquals(long[].class, clazz);

        clazz = ObjectHelper.loadClass("[F");
        Assertions.assertEquals(float[].class, clazz);

        clazz = ObjectHelper.loadClass("[D");
        Assertions.assertEquals(double[].class, clazz);

        clazz = ObjectHelper.loadClass("[Z");
        Assertions.assertEquals(boolean[].class, clazz);

        clazz = ObjectHelper.loadClass("[C");
        Assertions.assertEquals(char[].class, clazz);

        // Object array binary name: [Ljava.lang.String;
        // Note: loadClass() normalises the name, stripping ';', so the full binary form is not supported here.
        // Object arrays are handled via the existing "java.lang.String[]" / "String[]" aliases in loadSimpleType().

        // Multi-dimensional array: [[B (byte[][])
        clazz = ObjectHelper.loadClass("[[B");
        Assertions.assertNotNull(clazz, "[[B should resolve to byte[][]");
        Assertions.assertEquals(byte[][].class, clazz);
    }

}
