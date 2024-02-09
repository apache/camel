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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiException;
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
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeCompletion;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeSync;
import org.apache.camel.component.google.pubsub.consumer.CamelMessageReceiver;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GooglePubsubConsumer extends DefaultConsumer {

    private final Logger localLog;

    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private ExecutorService executor;
    private final List<Subscriber> subscribers;
    private final Set<ApiFuture<PullResponse>> pendingSynchronousPullResponses;

    GooglePubsubConsumer(GooglePubsubEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.subscribers = Collections.synchronizedList(new LinkedList<>());
        this.pendingSynchronousPullResponses = Collections.synchronizedSet(new HashSet<>());
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

        synchronized (subscribers) {
            if (!subscribers.isEmpty()) {
                localLog.info("Stopping subscribers for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
                subscribers.forEach(AbstractApiService::stopAsync);
            }
        }

        safeCancelSynchronousPullResponses();

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    private void safeCancelSynchronousPullResponses() {
        synchronized (pendingSynchronousPullResponses) {
            for (ApiFuture<PullResponse> pullResponseApiFuture : pendingSynchronousPullResponses) {
                try {
                    pullResponseApiFuture.cancel(true);
                } catch (Exception e) {
                    localLog.warn("Exception while cancelling pending synchronous pull response", e);
                }
            }
            pendingSynchronousPullResponses.clear();
        }
    }

    private class SubscriberWrapper implements Runnable {

        private final String subscriptionName;

        SubscriberWrapper() {
            subscriptionName = ProjectSubscriptionName.format(endpoint.getProjectId(), endpoint.getDestinationName());
        }

        @Override
        public void run() {
            try {
                if (localLog.isDebugEnabled()) {
                    localLog.debug("Subscribing to {}", subscriptionName);
                }

                if (endpoint.isSynchronousPull()) {
                    synchronousPull(subscriptionName);
                } else {
                    asynchronousPull(subscriptionName);
                }

                localLog.debug("Exit run for subscription {}", subscriptionName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                localLog.error("Failure getting messages from PubSub", e);
            } catch (Exception e) {
                localLog.error("Failure getting messages from PubSub", e);
            }
        }

        private void asynchronousPull(String subscriptionName) throws IOException {
            while (isRunAllowed() && !isSuspendingOrSuspended()) {
                MessageReceiver messageReceiver = new CamelMessageReceiver(GooglePubsubConsumer.this, endpoint, processor);

                Subscriber subscriber = endpoint.getComponent().getSubscriber(subscriptionName, messageReceiver, endpoint);
                try {
                    subscribers.add(subscriber);
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

        private void synchronousPull(String subscriptionName) throws ExecutionException, InterruptedException {
            while (isRunAllowed() && !isSuspendingOrSuspended()) {
                ApiFuture<PullResponse> synchronousPullResponseFuture = null;
                try (SubscriberStub subscriber = endpoint.getComponent().getSubscriberStub(endpoint)) {

                    PullRequest pullRequest = PullRequest.newBuilder()
                            .setMaxMessages(endpoint.getMaxMessagesPerPoll())
                            .setReturnImmediately(false)
                            .setSubscription(subscriptionName)
                            .build();

                    synchronousPullResponseFuture = subscriber.pullCallable().futureCall(pullRequest);
                    pendingSynchronousPullResponses.add(synchronousPullResponseFuture);
                    PullResponse pullResponse = synchronousPullResponseFuture.get();
                    for (ReceivedMessage message : pullResponse.getReceivedMessagesList()) {
                        PubsubMessage pubsubMessage = message.getMessage();
                        Exchange exchange = createExchange(true);
                        exchange.getIn().setBody(pubsubMessage.getData().toByteArray());

                        exchange.getIn().setHeader(GooglePubsubConstants.ACK_ID, message.getAckId());
                        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
                        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());
                        exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubsubMessage.getAttributesMap());

                        //existing subscriber can not be propagated, because it will be closed at the end of this block
                        //subscriber will be created at the moment of use
                        // (see  https://issues.apache.org/jira/browse/CAMEL-18447)
                        GooglePubsubAcknowledge acknowledge = new AcknowledgeSync(
                                () -> endpoint.getComponent().getSubscriberStub(endpoint), subscriptionName);

                        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
                            exchange.getExchangeExtension().addOnCompletion(new AcknowledgeCompletion(acknowledge));
                        } else {
                            exchange.getIn().setHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE, acknowledge);
                        }

                        try {
                            processor.process(exchange);
                        } catch (Exception e) {
                            getExceptionHandler().handleException(e);
                        }
                    }
                } catch (CancellationException e) {
                    localLog.debug("PubSub synchronous pull request cancelled", e);
                } catch (IOException e) {
                    localLog.error("I/O exception while getting messages from PubSub. Reconnecting.", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ApiException && ((ApiException) (e.getCause())).isRetryable()) {
                        localLog.error("Retryable API exception in getting messages from PubSub", e.getCause());
                    } else {
                        throw e;
                    }
                } finally {
                    if (synchronousPullResponseFuture != null) {
                        pendingSynchronousPullResponses.remove(synchronousPullResponseFuture);
                    }
                }
            }
        }
    }
}
