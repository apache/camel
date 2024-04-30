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

package org.apache.camel.component.kafka.consumer.support.interop;

import java.nio.ByteBuffer;

import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;

public class JMSDeserializer implements KafkaHeaderDeserializer {

    public boolean isLong(byte[] bytes) {
        return bytes.length == Long.BYTES;
    }

    private static long bytesToLong(byte[] bytes) {
        final ByteBuffer buffer = toByteBuffer(bytes, Long.BYTES);
        return buffer.getLong();
    }

    private static int bytesToInt(byte[] bytes) {
        final ByteBuffer buffer = toByteBuffer(bytes, Integer.BYTES);
        return buffer.getInt();
    }

    private static ByteBuffer toByteBuffer(byte[] bytes, int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    @Override
    public Object deserialize(String key, byte[] value) {
        if (key.startsWith("JMS")) {
            switch (key) {
                case "JMSDestination":
                    return new String(value);
                case "JMSDeliveryMode":
                    return bytesToInt(value);
                case "JMSTimestamp":
                    return bytesToLong(value);
                case "JMSCorrelationID":
                    return new String(value);
                case "JMSReplyTo":
                    return new String(value);
                case "JMSRedelivered":
                    return Boolean.parseBoolean(new String(value));
                case "JMSType":
                    return new String(value);
                case "JMSExpiration":
                    return isLong(value) ? bytesToLong(value) : bytesToInt(value);
                case "JMSPriority":
                    return bytesToInt(value);
                case "JMSMessageID":
                    return new String(value);
                default:
                    return value;
            }
        }

        return value;
    }
}
