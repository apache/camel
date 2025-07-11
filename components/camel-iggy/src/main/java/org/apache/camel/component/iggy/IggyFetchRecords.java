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
package org.apache.camel.component.iggy;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.consumergroup.Consumer;
import org.apache.iggy.identifier.ConsumerId;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.PolledMessages;
import org.apache.iggy.message.PollingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IggyFetchRecords implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(IggyFetchRecords.class);

    private final IggyConsumer iggyConsumer;
    private final IggyEndpoint endpoint;
    private final IggyConfiguration configuration;
    private final IggyBaseClient client;
    private final BridgeExceptionHandlerToErrorHandler bridgeExceptionHandlerToErrorHandler;

    private volatile boolean running;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private BigInteger offset;

    public IggyFetchRecords(IggyConsumer iggyConsumer, IggyEndpoint endpoint, IggyConfiguration configuration,
                            IggyBaseClient client, BridgeExceptionHandlerToErrorHandler bridgeExceptionHandlerToErrorHandler) {
        this.iggyConsumer = iggyConsumer;
        this.endpoint = endpoint;
        this.configuration = configuration;
        this.client = client;
        this.bridgeExceptionHandlerToErrorHandler = bridgeExceptionHandlerToErrorHandler;
        this.offset = configuration.getStartingOffset() == null ? null : BigInteger.valueOf(configuration.getStartingOffset());
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            if (iggyConsumer.isSuspending() || iggyConsumer.isSuspended()) {
                LOG.trace("Consumer is suspended. Skipping message polling.");
                try {
                    Thread.sleep(1000); // Sleep for a bit to avoid busy-waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            if (polling.compareAndSet(false, true)) {
                try {
                    pollMessages();
                } catch (Exception e) {
                    bridgeExceptionHandlerToErrorHandler.handleException(e);
                } finally {
                    polling.set(false);
                }
            }
        }
    }

    private void pollMessages() {
        try {
            StreamId streamId = StreamId.of(configuration.getStreamName());
            TopicId topicId = TopicId.of(endpoint.getTopicName());
            ConsumerId consumerId = ConsumerId.of(configuration.getConsumerGroupName());

            PolledMessages polledMessages;
            if (configuration.isAutoCommit()) {
                polledMessages = client.messages()
                        .pollMessages(streamId,
                                topicId,
                                Optional.ofNullable(configuration.getPartitionId()),
                                Consumer.group(consumerId),
                                resolvePollingStrategy(),
                                configuration.getPollBatchSize(),
                                configuration.isAutoCommit());
            } else {
                polledMessages = client.messages()
                        .pollMessages(streamId,
                                topicId,
                                Optional.ofNullable(configuration.getPartitionId()),
                                Consumer.group(consumerId),
                                PollingStrategy.offset(offset),
                                configuration.getPollBatchSize(),
                                false);

                // Update offset
                offset = offset.add(BigInteger.valueOf(polledMessages.count()));
            }

            LOG.debug("Fetched {} messages from partition {}, current offset {}",
                    polledMessages.count(),
                    polledMessages.partitionId(),
                    configuration.isAutoCommit() ? polledMessages.currentOffset() : offset);

            for (org.apache.iggy.message.Message message : polledMessages.messages()) {
                Exchange exchange = createExchange(message);
                try {
                    iggyConsumer.getProcessor().process(exchange);
                } catch (Exception e) {
                    bridgeExceptionHandlerToErrorHandler.handleException(e);
                }
            }
        } catch (Exception e) {
            bridgeExceptionHandlerToErrorHandler.handleException("Error polling messages from Iggy", e);
        }
    }

    private PollingStrategy resolvePollingStrategy() {
        switch (configuration.getPollingStrategy()) {
            case "first":
                return PollingStrategy.first();
            case "last":
                return PollingStrategy.last();
            default:
                return PollingStrategy.next();
        }
    }

    private Exchange createExchange(org.apache.iggy.message.Message message) {
        Exchange exchange = iggyConsumer.createExchange(true);

        exchange.getIn().setBody(new String(message.payload())); // TODO is it ok?
        exchange.getIn().setHeader(IggyConstants.MESSAGE_ID, message.header().id());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_OFFSET, message.header().offset());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_ORIGIN_TIMESTAMP, message.header().originTimestamp());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_TIMESTAMP, message.header().timestamp());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_CHECKSUM, message.header().checksum());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_LENGTH, message.header().payloadLength());
        exchange.getIn().setHeader(IggyConstants.MESSAGE_SIZE, message.getSize());

        message.userHeaders().ifPresent(userHeaders -> {
            Map<String, Object> stringUserHeaders = userHeaders.entrySet().stream().collect(Collectors.toMap(
                    k -> k.getKey(),
                    hv -> hv.getValue().value() // TODO this way `HeaderKind kind` will be lost
            ));

            exchange.getIn().setHeaders(stringUserHeaders);
        });

        return exchange;
    }

    public void stop() {
        running = false;
    }
}
