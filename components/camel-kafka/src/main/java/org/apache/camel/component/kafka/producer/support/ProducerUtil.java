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
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.clients.producer.RecordMetadata;
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

    static void setException(Object body, Exception e) {
        if (e != null) {
            if (body instanceof Exchange) {
                ((Exchange) body).setException(e);
            }
            if (body instanceof Message && ((Message) body).getExchange() != null) {
                ((Message) body).getExchange().setException(e);
            }
        }
    }

    static void setRecordMetadata(Object body, RecordMetadata recordMetadata) {
        final List<RecordMetadata> recordMetadataList = Collections.singletonList(recordMetadata);

        setRecordMetadata(body, recordMetadataList);
    }

    public static void setRecordMetadata(Object body, List<RecordMetadata> recordMetadataList) {
        if (body instanceof Exchange) {
            Exchange ex = (Exchange) body;
            ex.getMessage().setHeader(KafkaConstants.KAFKA_RECORD_META, recordMetadataList);
        }
        if (body instanceof Message) {
            Message msg = (Message) body;
            msg.setHeader(KafkaConstants.KAFKA_RECORD_META, recordMetadataList);
        }
    }
}
