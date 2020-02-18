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

import java.util.concurrent.ExecutorService;

import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.base.Strings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.consumer.CamelMessageReceiver;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GooglePubsubConsumer extends DefaultConsumer {

    private Logger localLog;

    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private ExecutorService executor;

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

            while (isRunAllowed() && !isSuspendingOrSuspended()) {

                MessageReceiver messageReceiver = new CamelMessageReceiver(endpoint, processor);

                Subscriber subscriber = endpoint.getComponent().getSubscriber(subscriptionName, messageReceiver);
                try {
                    subscriber.startAsync().awaitRunning();
                    subscriber.awaitTerminated();
                } catch (Exception e) {
                    localLog.error("Failure getting messages from PubSub", e);
                } finally {
                    subscriber.stopAsync();
                }
            }
        }
    }
}