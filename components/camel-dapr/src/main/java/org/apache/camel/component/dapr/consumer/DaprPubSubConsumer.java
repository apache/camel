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

package org.apache.camel.component.dapr.consumer;

import java.io.Closeable;

import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.SubscriptionListener;
import io.dapr.client.domain.CloudEvent;
import io.dapr.utils.TypeRef;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class DaprPubSubConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DaprPubSubConsumer.class);
    private final String pubSubName;
    private final String topic;
    private DaprPreviewClient client;
    private Closeable subscription;

    public DaprPubSubConsumer(final DaprEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        pubSubName = endpoint.getConfiguration().getPubSubName();
        topic = endpoint.getConfiguration().getTopic();
        client = endpoint.getConfiguration().getPreviewClient();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (client == null && (ObjectHelper.isEmpty(pubSubName) || ObjectHelper.isEmpty(topic))) {
            throw new IllegalArgumentException("pubSubName and topic are mandatory for subscribe operation");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Creating connection to Dapr PubSub");

        if (client == null) {
            client = new DaprClientBuilder().buildPreviewClient();
        }
        subscription =
                client.subscribeToEvents(pubSubName, topic, new DaprSubscriptionListener(), TypeRef.get(byte[].class));
    }

    @Override
    protected void doStop() throws Exception {
        if (subscription != null) {
            subscription.close();
        }
        if (client != null) {
            client.close();
        }

        // shutdown camel consumer
        super.doStop();
    }

    public DaprConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public DaprEndpoint getEndpoint() {
        return (DaprEndpoint) super.getEndpoint();
    }

    private Exchange createServiceBusExchange(final CloudEvent<byte[]> cloudEvent) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body
        message.setBody(cloudEvent.getData());

        // set headers
        message.setHeader(DaprConstants.PUBSUB_NAME, cloudEvent.getPubsubName());
        message.setHeader(DaprConstants.TOPIC, cloudEvent.getTopic());
        message.setHeader(DaprConstants.ID, cloudEvent.getId());
        message.setHeader(DaprConstants.SOURCE, cloudEvent.getSource());
        message.setHeader(DaprConstants.TYPE, cloudEvent.getType());
        message.setHeader(DaprConstants.SPECIFIC_VERSION, cloudEvent.getSpecversion());
        message.setHeader(DaprConstants.DATA_CONTENT_TYPE, cloudEvent.getDatacontenttype());
        message.setHeader(DaprConstants.BINARY_DATA, cloudEvent.getBinaryData());
        message.setHeader(DaprConstants.TIME, cloudEvent.getTime());
        message.setHeader(DaprConstants.TRACE_PARENT, cloudEvent.getTraceParent());
        message.setHeader(DaprConstants.TRACE_STATE, cloudEvent.getTraceState());

        return exchange;
    }

    protected class DaprSubscriptionListener implements SubscriptionListener<byte[]> {

        @Override
        public Mono<Status> onEvent(CloudEvent<byte[]> cloudEvent) {
            final Exchange exchange = createServiceBusExchange(cloudEvent);

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);

            return Mono.just(Status.SUCCESS);
        }

        @Override
        public void onError(RuntimeException ex) {
            LOG.error("Error from Dapr client: {}", ex.getMessage(), ex);
        }
    }
}
