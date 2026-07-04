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
package org.apache.camel.http.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpHelperDeserializationTest {

    private static byte[] serialize(Serializable object) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
        }
        return bos.toByteArray();
    }

    @Test
    public void configuredFilterRejectsDeniedClass() throws Exception {
        // use a real object (not a String, which is serialized as TC_STRING and bypasses the class filter)
        InputStream is = new ByteArrayInputStream(serialize(new ArrayList<>(List.of("a", "b"))));
        // a configured filter that denies everything must reject the ArrayList class
        assertThrows(InvalidClassException.class,
                () -> HttpHelper.deserializeJavaObjectFromStream(is, null, "!*"));
    }

    @Test
    public void configuredFilterAllowsPermittedClass() throws Exception {
        InputStream is = new ByteArrayInputStream(serialize(new ArrayList<>(List.of("a", "b"))));
        // a configured filter that allows the java.* packages must let the ArrayList (and its elements) through
        assertEquals(List.of("a", "b"), HttpHelper.deserializeJavaObjectFromStream(is, null, "java.**;!*"));
    }

    @Test
    public void defaultFilterDeniesJavaNetPackage() throws Exception {
        InputStream is = new ByteArrayInputStream(serialize(new InetSocketAddress("localhost", 80)));
        // with no configured/JVM filter, the default Camel filter denies java.net.**
        assertThrows(InvalidClassException.class,
                () -> HttpHelper.deserializeJavaObjectFromStream(is, null));
    }
}
