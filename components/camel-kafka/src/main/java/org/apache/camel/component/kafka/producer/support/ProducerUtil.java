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

package org.apache.camel.component.kafka.producer.support;

import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.common.utils.Bytes;

public final class ProducerUtil {

    private ProducerUtil() {

    }

    public static Object tryConvertToSerializedType(Exchange exchange, Object object, String valueSerializer) {
        Object answer = null;

        if (exchange == null) {
            return object;
        }

        if (KafkaConstants.KAFKA_DEFAULT_SERIALIZER.equals(valueSerializer)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.ByteArraySerializer".equals(valueSerializer)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(byte[].class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.ByteBufferSerializer".equals(valueSerializer)) {
            answer = exchange.getContext().getTypeConverter().tryConvertTo(ByteBuffer.class, exchange, object);
        } else if ("org.apache.kafka.common.serialization.BytesSerializer".equals(valueSerializer)) {
            // we need to convert to byte array first
            byte[] array = exchange.getContext().getTypeConverter().tryConvertTo(byte[].class, exchange, object);
            if (array != null) {
                answer = new Bytes(array);
            }
        }

        return answer != null ? answer : object;
    }
}
