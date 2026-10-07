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

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DefaultKafkaHeaderDeserializerTest {

    private final KafkaHeaderDeserializer deserializer = new DefaultKafkaHeaderDeserializer();

    @Test
    public void shouldDeserializeAsIs() {
        byte[] value = new byte[] { 0, 4, -2, 54, 126 };

        Object deserializedValue = deserializer.deserialize("someKey", value);

        assertThat(deserializedValue, CoreMatchers.instanceOf(byte[].class));
        assertArrayEquals(value, (byte[]) deserializedValue);
    }

}
