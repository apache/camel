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
package org.apache.camel.component.google.pubsublite;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import java.time.Duration;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsublite.cloudpubsub.Subscriber;
import com.google.common.base.Strings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsublite.consumer.CamelMessageReceiver;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GooglePubsubLiteConsumer extends DefaultConsumer {

    private Logger localLog;

    private final GooglePubsubLiteEndpoint endpoint;
    private final Processor processor;
    private ExecutorService executor;
    private final List<Subscriber> subscribers;

    GooglePubsubLiteConsumer(GooglePubsubLiteEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.subscribers = Collections.synchronizedList(new LinkedList<>());
        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        localLog = LoggerFactory.getLogger(loggerId);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        localLog.info("Starting Google PubSub Lite consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
        executor = endpoint.createExecutor(this);
        for (int i = 0; i < endpoint.getConcurrentConsumers(); i++) {
            executor.submit(new SubscriberWrapper());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        localLog.info("Stopping Google PubSub Lite consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        synchronized (subscribers) {
            if (!subscribers.isEmpty()) {
                localLog.info("Stopping subscribers for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
                subscribers.forEach(Subscriber::stopAsync);
            }
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

        private final String subscriptionName;

        SubscriberWrapper() {
            subscriptionName
                    = ProjectSubscriptionName.format(endpoint.getProjectId().toString(), endpoint.getDestinationName());
        }

        @Override
        public void run() {
            try {
                if (localLog.isDebugEnabled()) {
                    localLog.debug("Subscribing to {}", subscriptionName);
                }

                while (isRunAllowed() && !isSuspendingOrSuspended()) {
                    MessageReceiver messageReceiver
                            = new CamelMessageReceiver(GooglePubsubLiteConsumer.this, endpoint, processor);

                    Subscriber subscriber = endpoint.getComponent().getSubscriber(messageReceiver, endpoint);
                    boolean subscriberAdded = false;
                    try {
                        subscriber.startAsync().awaitRunning();
                        // Only add to list after successful startup
                        subscribers.add(subscriber);
                        subscriberAdded = true;
                        subscriber.awaitTerminated();
                    } catch (Exception e) {
                        // Remove from list if it was added
                        if (subscriberAdded) {
                            subscribers.remove(subscriber);
                        }

                        // Check if error is recoverable
                        boolean isRecoverable = false;
                        if (e instanceof ApiException) {
                            isRecoverable = ((ApiException) e).isRetryable();
                        } else if (e.getCause() instanceof ApiException) {
                            isRecoverable = ((ApiException) e.getCause()).isRetryable();
                        }

                        if (isRecoverable) {
                            localLog.error("Retryable error getting messages from PubSub Lite", e);
                        } else {
                            localLog.error("Non-recoverable error getting messages from PubSub Lite, stopping subscriber loop", e);
                        }

                        // allow camel error handler to be aware
                        if (endpoint.isBridgeErrorHandler()) {
                            getExceptionHandler().handleException(e);
                        }

                        // For non-recoverable errors, exit the loop
                        if (!isRecoverable) {
                            break;
                        }

                        // Add backoff delay for recoverable errors to prevent tight loop
                        Tasks.foregroundTask()
                                .withBudget(Budgets.iterationBudget()
                                        .withMaxIterations(1)
                                        .withInterval(Duration.ofSeconds(5))
                                        .build())
                                .withName("PubSubLiteRetryDelay")
                                .build()
                                .run(getEndpoint().getCamelContext(), () -> true);
                    } finally {
                        localLog.debug("Stopping async subscriber {}", subscriptionName);
                        subscriber.stopAsync();
                        // Ensure cleanup from list
                        if (subscriberAdded) {
                            subscribers.remove(subscriber);
                        }
                    }
                }

                localLog.debug("Exit run for subscription {}", subscriptionName);
            } catch (Exception e) {
                localLog.error("Failure getting messages from PubSub Lite", e);

                // allow camel error handler to be aware
                if (endpoint.isBridgeErrorHandler()) {
                    getExceptionHandler().handleException(e);
                }
            }
        }
    }
}
