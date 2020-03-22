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
package org.apache.camel.component.google.pubsub;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.google.api.core.AbstractApiService;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.common.base.Strings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeSync;
import org.apache.camel.component.google.pubsub.consumer.CamelMessageReceiver;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GooglePubsubConsumer extends DefaultConsumer {

    private Logger localLog;

    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private ExecutorService executor;
    private List<Subscriber> subscribers;

    GooglePubsubConsumer(GooglePubsubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        localLog = LoggerFactory.getLogger(loggerId);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        localLog.info("Starting Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
        executor = endpoint.createExecutor();
        for (int i = 0; i < endpoint.getConcurrentConsumers(); i++) {
            executor.submit(new SubscriberWrapper());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        localLog.info("Stopping Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        if (subscribers != null && !subscribers.isEmpty()) {
            localLog.info("Stopping subscribers for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
            subscribers.forEach(AbstractApiService::stopAsync);
        }

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    private class SubscriberWrapper implements Runnable {

        @Override
        public void run() {
            String subscriptionName = ProjectSubscriptionName.format(endpoint.getProjectId(), endpoint.getDestinationName());

            if (localLog.isDebugEnabled()) {
                localLog.debug("Subscribing to {}", subscriptionName);
            }


            if (endpoint.isSynchronousPull()) {
                synchronousPull(subscriptionName);
            } else {
                asynchronousPull(subscriptionName);
            }

            localLog.debug("Exit run for subscription {}", subscriptionName);
        }

        private void asynchronousPull(String subscriptionName) {
            while (isRunAllowed() && !isSuspendingOrSuspended()) {
                MessageReceiver messageReceiver = new CamelMessageReceiver(endpoint, processor);

                Subscriber subscriber = endpoint.getComponent().getSubscriber(subscriptionName, messageReceiver);
                subscribers.add(subscriber);
                try {
                    subscriber.startAsync().awaitRunning();
                    subscriber.awaitTerminated();
                } catch (Exception e) {
                    localLog.error("Failure getting messages from PubSub", e);
                } finally {
                    localLog.debug("Stopping async subscriber {}", subscriptionName);
                    subscriber.stopAsync();
                }
            }
        }

        private void synchronousPull(String subscriptionName) {
            while (isRunAllowed() && !isSuspendingOrSuspended()) {
                try (SubscriberStub subscriber = endpoint.getComponent().getSubscriberStub()) {

                    PullRequest pullRequest = PullRequest.newBuilder()
                            .setMaxMessages(endpoint.getMaxMessagesPerPoll())
                            .setReturnImmediately(false)
                            .setSubscription(subscriptionName)
                            .build();

                    PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
                    for (ReceivedMessage message : pullResponse.getReceivedMessagesList()) {
                        PubsubMessage pubsubMessage = message.getMessage();
                        Exchange exchange = endpoint.createExchange();
                        exchange.getIn().setBody(pubsubMessage.getData().toByteArray());

                        exchange.getIn().setHeader(GooglePubsubConstants.ACK_ID, message.getAckId());
                        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
                        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());

                        if (null != pubsubMessage.getAttributesMap()) {
                            exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubsubMessage.getAttributesMap());
                        }

                        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
                            exchange.adapt(ExtendedExchange.class).addOnCompletion(new AcknowledgeSync(subscriber, subscriptionName));
                        }

                        try {
                            processor.process(exchange);
                        } catch (Throwable e) {
                            exchange.setException(e);
                        }
                    }
                } catch (IOException e) {
                    localLog.error("Failure getting messages from PubSub", e);
                }
            }
        }
    }
}