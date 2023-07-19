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
package org.apache.camel.component.kafka.serde;

import java.util.Arrays;
import java.util.Collection;

import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DefaultKafkaHeaderSerializerTest {

    private final DefaultKafkaHeaderSerializer serializer = new DefaultKafkaHeaderSerializer();

    @ParameterizedTest
    @MethodSource("primeNumbers")
    public void serialize(Object value, byte[] expectedResult) {
        serializer.setCamelContext(new DefaultCamelContext());
        byte[] result = serializer.serialize("someKey", value);

        assertArrayEquals(expectedResult, result);
    }

    public static Collection<Object[]> primeNumbers() {
        return Arrays.asList(new Object[][] {
                { Boolean.TRUE, "true".getBytes() }, // boolean
                { -12, new byte[] { -1, -1, -1, -12 } }, // integer
                { 19L, new byte[] { 0, 0, 0, 0, 0, 0, 0, 19 } }, // long
                { 22.0D, new byte[] { 64, 54, 0, 0, 0, 0, 0, 0 } }, // double
                { "someValue", "someValue".getBytes() }, // string
                { new byte[] { 0, 2, -43 }, new byte[] { 0, 2, -43 } }, // byte[]
                { new TextNode("foo"), "foo".getBytes() }, // jackson TextNode
                { null, null }, // null
                { new Object(), null } // unknown
                                      // type
        });
    }
}
