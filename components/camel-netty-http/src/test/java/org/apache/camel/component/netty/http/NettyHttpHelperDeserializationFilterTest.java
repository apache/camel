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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;

import com.example.external.NotAllowedSerializable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyHttpHelperDeserializationFilterTest {

    @Test
    public void testDeserializeAllowlistedType() throws Exception {
        InputStream is = serialize("hello");
        Object value = NettyHttpHelper.deserializeJavaObjectFromStream(is);
        assertInstanceOf(String.class, value);
        assertEquals("hello", value);
    }

    @Test
    public void testDefaultFilterRejectsUnlistedType() throws Exception {
        InputStream is = serialize(new NotAllowedSerializable("blocked"));
        assertThrows(InvalidClassException.class, () -> NettyHttpHelper.deserializeJavaObjectFromStream(is));
    }

    @Test
    public void testConfiguredFilterAllowsExternalType() throws Exception {
        InputStream is = serialize(new NotAllowedSerializable("allowed"));
        String filter = "com.example.external.*;java.**;!*";
        Object value = NettyHttpHelper.deserializeJavaObjectFromStream(is, filter);
        assertInstanceOf(NotAllowedSerializable.class, value);
        assertEquals("allowed", ((NotAllowedSerializable) value).getValue());
    }

    @Test
    public void testConfiguredFilterStillRejectsUnlistedType() throws Exception {
        InputStream is = serialize(new NotAllowedSerializable("blocked"));
        String filter = "java.**;!*";
        assertThrows(InvalidClassException.class, () -> NettyHttpHelper.deserializeJavaObjectFromStream(is, filter));
    }

    private static InputStream serialize(Object value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
