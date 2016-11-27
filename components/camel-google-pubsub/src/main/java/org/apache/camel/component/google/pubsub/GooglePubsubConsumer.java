/**
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
package org.apache.camel.component.google.pubsub;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PullRequest;
import com.google.api.services.pubsub.model.PullResponse;
import com.google.api.services.pubsub.model.ReceivedMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.pubsub.consumer.ExchangeAckTransaction;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

class GooglePubsubConsumer extends DefaultConsumer {

    private Logger localLog;

    private ExecutorService executor;
    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private final Synchronization ackStrategy;

    GooglePubsubConsumer(GooglePubsubEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.ackStrategy = new ExchangeAckTransaction(this.endpoint);

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        localLog = LoggerFactory.getLogger(loggerId);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        localLog.info("Starting Google PubSub consumer");
        executor = endpoint.createExecutor();
        for (int i = 0; i < endpoint.getConcurrentConsumers(); i++) {

            executor.submit(new PubsubPoller(i + ""));

        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        localLog.info("Stopping Google PubSub consumer");

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    private class PubsubPoller implements Runnable {

        private final String subscriptionFullName;
        private final String threadId;

        PubsubPoller(String id) {
            this.subscriptionFullName = String.format("projects/%s/subscriptions/%s",
                                                      GooglePubsubConsumer.this.endpoint.getProjectId(),
                                                      GooglePubsubConsumer.this.endpoint.getDestinationName());
            this.threadId = GooglePubsubConsumer.this.endpoint.getDestinationName() + "-" + "Thread " + id;
        }

        @Override
        public void run() {
            try {
                if (localLog.isDebugEnabled()) {
                    localLog.debug("Subscribing {} to {}", threadId, subscriptionFullName);
                }

                while (isRunAllowed() && !isSuspendingOrSuspended()) {
                    PullRequest pullRequest = new PullRequest().setMaxMessages(endpoint.getMaxMessagesPerPoll());
                    PullResponse pullResponse;
                    try {
                        if (localLog.isTraceEnabled()) {
                            localLog.trace("Polling : {}", threadId);
                        }
                        pullResponse = GooglePubsubConsumer.this.endpoint
                                               .getPubsub()
                                               .projects()
                                               .subscriptions()
                                               .pull(subscriptionFullName, pullRequest)
                                               .execute();
                    } catch (SocketTimeoutException ste) {
                        if (localLog.isTraceEnabled()) {
                            localLog.trace("Socket timeout : {}", threadId);
                        }
                        continue;
                    }

                    List<ReceivedMessage> receivedMessages = pullResponse.getReceivedMessages();

                    for (ReceivedMessage receivedMessage : receivedMessages) {
                        PubsubMessage pubsubMessage = receivedMessage.getMessage();

                        byte[] body = pubsubMessage.decodeData();

                        if (localLog.isTraceEnabled()) {
                            localLog.trace("Received message ID : {}", pubsubMessage.getMessageId());
                        }

                        Exchange exchange = endpoint.createExchange();
                        exchange.getIn().setBody(body);

                        exchange.getIn().setHeader(GooglePubsubConstants.ACK_ID, receivedMessage.getAckId());
                        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
                        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());

                        if (null != receivedMessage.getMessage().getAttributes()) {
                            exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, receivedMessage.getMessage().getAttributes());
                        }

                        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
                            exchange.addOnCompletion(GooglePubsubConsumer.this.ackStrategy);
                        }

                        try {
                            processor.process(exchange);
                        } catch (Throwable e) {
                            exchange.setException(e);
                        }
                    }
                }
            } catch (Exception e) {
                localLog.error("Requesting messages from PubSub Failed:", e);
                RuntimeCamelException rce = wrapRuntimeCamelException(e);
                throw rce;
            }
        }
    }
}

