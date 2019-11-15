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
package org.apache.camel.component.pulsar.utils.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.pulsar.client.api.Message;

import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.EVENT_TIME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.KEY;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.KEY_BYTES;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.MESSAGE_ID;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PRODUCER_NAME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PROPERTIES;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.PUBLISH_TIME;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.SEQUENCE_ID;
import static org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders.TOPIC_NAME;

public final class PulsarMessageUtils {

    private PulsarMessageUtils() {
    }

    public static Exchange updateExchange(final Message<byte[]> message, final Exchange input) {
        final Exchange output = input.copy();

        org.apache.camel.Message msg = output.getIn();

        msg.setHeader(EVENT_TIME, message.getEventTime());
        msg.setHeader(MESSAGE_ID, message.getMessageId());
        msg.setHeader(KEY, message.getKey());
        msg.setHeader(KEY_BYTES, message.getKeyBytes());
        msg.setHeader(PRODUCER_NAME, message.getProducerName());
        msg.setHeader(TOPIC_NAME, message.getTopicName());
        msg.setHeader(SEQUENCE_ID, message.getSequenceId());
        msg.setHeader(PUBLISH_TIME, message.getPublishTime());
        msg.setHeader(PROPERTIES, message.getProperties());

        msg.setBody(message.getValue());

        output.setIn(msg);

        return output;
    }

    public static Exchange updateExchangeWithException(final Exception exception, final Exchange input) {
        final Exchange output = input.copy();

        output.setException(exception);

        return output;
    }

    public static byte[] serialize(final Object body) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        try {
            outputStream.writeObject(body);
            return byteArrayOutputStream.toByteArray();
        } catch (NotSerializableException exception) {
            throw new RuntimeCamelException(exception);
        } finally {
            byteArrayOutputStream.close();
            outputStream.close();
        }
    }
}
