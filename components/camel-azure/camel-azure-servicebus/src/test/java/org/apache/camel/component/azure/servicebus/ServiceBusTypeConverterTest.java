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
package org.apache.camel.component.azure.servicebus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.azure.core.util.BinaryData;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBusTypeConverterTest extends CamelTestSupport {

    @Test
    void testBinaryDataToString() throws Exception {
        String result = context.getTypeConverter().mandatoryConvertTo(String.class, BinaryData.fromString("Hello Camel"));

        assertThat(result).isEqualTo("Hello Camel");
    }

    @Test
    void testBinaryDataToBytes() throws Exception {
        byte[] result = context.getTypeConverter().mandatoryConvertTo(byte[].class, BinaryData.fromString("Hello Camel"));

        assertThat(result).isEqualTo("Hello Camel".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testBinaryDataToInputStream() throws Exception {
        InputStream result
                = context.getTypeConverter().mandatoryConvertTo(InputStream.class, BinaryData.fromString("Hello Camel"));

        try (InputStream is = result) {
            assertThat(is.readAllBytes()).isEqualTo("Hello Camel".getBytes(StandardCharsets.UTF_8));
        }
    }
}
