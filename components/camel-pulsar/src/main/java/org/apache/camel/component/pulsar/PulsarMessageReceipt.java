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

import org.apache.camel.Exchange;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClientException;

/**
 * Acknowledge the receipt of a message using the Pulsar consumer.
 * <p>
 * Available on the {@link Exchange} if
 * {@link PulsarConfiguration#isAllowManualAcknowledgement()} is true. An
 * alternative to the default may be provided by implementing
 * {@link PulsarMessageReceiptFactory}.
 */
public interface PulsarMessageReceipt {

    /**
     * Acknowledge receipt of this message synchronously.
     *
     * @see org.apache.pulsar.client.api.Consumer#acknowledge(MessageId)
     */
    void acknowledge() throws PulsarClientException;

    /**
     * Acknowledge receipt of all of the messages in the stream up to and
     * including this message synchronously.
     *
     * @see org.apache.pulsar.client.api.Consumer#acknowledgeCumulative(MessageId)
     */
    void acknowledgeCumulative() throws PulsarClientException;

    /**
     * Acknowledge receipt of this message asynchronously.
     *
     * @see org.apache.pulsar.client.api.Consumer#acknowledgeAsync(MessageId)
     */
    CompletableFuture<Void> acknowledgeAsync();

    /**
     * Acknowledge receipt of all of the messages in the stream up to and
     * including this message asynchronously.
     *
     * @see org.apache.pulsar.client.api.Consumer#acknowledgeCumulativeAsync(MessageId)
     */
    CompletableFuture<Void> acknowledgeCumulativeAsync();

    /**
     * Acknowledge the failure to process this message.
     *
     * @see org.apache.pulsar.client.api.Consumer#negativeAcknowledge(MessageId)
     *      Note: Available in Puslar 2.4.0. Implementations with earlier
     *      versions should return an
     *      {@link java.lang.UnsupportedOperationException}.
     */
    void negativeAcknowledge();

}
