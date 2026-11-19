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
package org.apache.camel.component.google.pubsub.consumer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcknowledgeSync implements GooglePubsubAcknowledge {

    private static final Logger LOG = LoggerFactory.getLogger(AcknowledgeSync.class);

    //Supplier cannot be used because of thrown exception (Callback used instead)
    private final Callable<SubscriberStub> subscriberStubSupplier;
    private final String subscriptionName;
    // the ack id of the message this acknowledges, or null to take the ack ids from the exchange given
    private final String ackId;

    /**
     * Acknowledges the messages whose ack ids the exchange given to {@link #ack(Exchange)} or {@link #nack(Exchange)}
     * carries in its {@link GooglePubsubConstants#ACK_ID} header, and the grouped exchanges of its body.
     */
    public AcknowledgeSync(Callable<SubscriberStub> subscriberStubSupplier, String subscriptionName) {
        this(subscriberStubSupplier, subscriptionName, null);
    }

    /**
     * Acknowledges the message with the given ack id, whatever the exchange given to {@link #ack(Exchange)} or
     * {@link #nack(Exchange)} carries: the route may have changed or removed the {@link GooglePubsubConstants#ACK_ID}
     * header, or replaced the message.
     *
     * @param ackId the ack id the message was received with, or {@code null} to take the ack ids from the exchange
     *              given, as {@link #AcknowledgeSync(Callable, String)} does
     */
    public AcknowledgeSync(Callable<SubscriberStub> subscriberStubSupplier, String subscriptionName, String ackId) {
        this.subscriberStubSupplier = subscriberStubSupplier;
        this.subscriptionName = subscriptionName;
        this.ackId = ackId;
    }

    @Override
    public void ack(Exchange exchange) {
        List<String> ackIds = getAckIdList(exchange);
        if (ackIds.isEmpty()) {
            // the API requires at least one ack id; this may run in an on completion callback, so do not throw
            LOG.warn("No {} header on exchange {} nor on its grouped exchanges: no message is acknowledged,"
                     + " so Pub/Sub delivers the messages again after their ack deadline",
                    GooglePubsubConstants.ACK_ID, exchange.getExchangeId());
            return;
        }
        AcknowledgeRequest ackRequest = AcknowledgeRequest.newBuilder()
                .addAllAckIds(ackIds)
                .setSubscription(subscriptionName).build();
        try (SubscriberStub subscriber = subscriberStubSupplier.call()) {
            subscriber.acknowledgeCallable().call(ackRequest);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void nack(Exchange exchange) {
        // There is no explicit nack on the subscriber client. Using modifyAckDeadline with 0 seconds
        // is the recommended way to nack a message. https://github.com/googleapis/python-pubsub/pull/123
        List<String> ackIds = getAckIdList(exchange);
        if (ackIds.isEmpty()) {
            LOG.warn("No {} header on exchange {} nor on its grouped exchanges: no message is negatively acknowledged,"
                     + " so Pub/Sub delivers the messages again after their ack deadline",
                    GooglePubsubConstants.ACK_ID, exchange.getExchangeId());
            return;
        }
        ModifyAckDeadlineRequest nackRequest = ModifyAckDeadlineRequest.newBuilder()
                .addAllAckIds(ackIds)
                .setSubscription(subscriptionName)
                .setAckDeadlineSeconds(0).build();

        try (SubscriberStub subscriber = subscriberStubSupplier.call()) {
            subscriber.modifyAckDeadlineCallable().call(nackRequest);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private List<String> getAckIdList(Exchange exchange) {
        if (ackId != null) {
            return List.of(ackId);
        }
        // an aggregated exchange (e.g. from GroupedExchangeAggregationStrategy) carries the grouped exchanges as its
        // body: each one holds the ack id of its own message, and the aggregated exchange may be one of them
        Set<String> ackIds = new LinkedHashSet<>();

        if (exchange.getIn().getBody() instanceof List<?> grouped) {
            for (Object body : grouped) {
                if (body instanceof Exchange groupedExchange) {
                    addAckId(ackIds, groupedExchange);
                }
            }
        }

        addAckId(ackIds, exchange);

        return new ArrayList<>(ackIds);
    }

    private static void addAckId(Set<String> ackIds, Exchange exchange) {
        String ackId = exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID, String.class);
        if (null != ackId) {
            ackIds.add(ackId);
        }
    }
}
