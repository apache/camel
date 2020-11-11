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
package org.apache.camel.component.pulsar;

import java.util.concurrent.CompletableFuture;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClientException;

public class DefaultPulsarMessageReceipt implements PulsarMessageReceipt {

    private final Consumer consumer;

    private final MessageId messageId;

    public DefaultPulsarMessageReceipt(Consumer consumer, MessageId messageId) {
        this.consumer = consumer;
        this.messageId = messageId;
    }

    @Override
    public void acknowledge() throws PulsarClientException {
        consumer.acknowledge(messageId);
    }

    @Override
    public void acknowledgeCumulative() throws PulsarClientException {
        consumer.acknowledgeCumulative(messageId);
    }

    @Override
    public CompletableFuture<Void> acknowledgeAsync() {
        return consumer.acknowledgeAsync(messageId);
    }

    @Override
    public CompletableFuture<Void> acknowledgeCumulativeAsync() {
        return consumer.acknowledgeCumulativeAsync(messageId);
    }

    @Override
    public void negativeAcknowledge() {
        consumer.negativeAcknowledge(messageId);
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public MessageId getMessageId() {
        return messageId;
    }
}
