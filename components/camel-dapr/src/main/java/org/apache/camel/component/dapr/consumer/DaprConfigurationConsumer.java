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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.SubscribeConfigurationRequest;
import io.dapr.client.domain.SubscribeConfigurationResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class DaprConfigurationConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DaprConfigurationConsumer.class);
    private final String configStore;
    private final List<String> configKeys;
    private final DaprClient client;
    private String subscriptionId;

    public DaprConfigurationConsumer(final DaprEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        configStore = endpoint.getConfiguration().getConfigStore();
        configKeys = endpoint.getConfiguration().getConfigKeysAsList();
        client = endpoint.getClient();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (ObjectHelper.isEmpty(configStore) || ObjectHelper.isEmpty(configKeys)) {
            throw new IllegalArgumentException(
                    "configStore and configKeys are mandatory for subscribe configuration operation");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Creating connection to Dapr Configuration");

        SubscribeConfigurationRequest configRequest = new SubscribeConfigurationRequest(configStore, configKeys);
        Flux<SubscribeConfigurationResponse> subscription = client.subscribeConfiguration(configRequest);

        subscription.subscribe((response) -> {
            // first ever response contains the subscription id
            if (response.getItems() == null || response.getItems().isEmpty()) {
                subscriptionId = response.getSubscriptionId();
                LOG.debug("App subscribed to config changes with subscription id: {}", subscriptionId);
            } else {
                final Exchange exchange = createServiceBusExchange(response);

                // use default consumer callback
                AsyncCallback cb = defaultConsumerCallback(exchange, true);
                getAsyncProcessor().process(exchange, cb);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.unsubscribeConfiguration(subscriptionId, configStore);
            client.close();
        }

        // shutdown camel consumer
        super.doStop();
    }

    @Override
    public DaprEndpoint getEndpoint() {
        return (DaprEndpoint) super.getEndpoint();
    }

    private Exchange createServiceBusExchange(final SubscribeConfigurationResponse response) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        Map<String, String> body = new HashMap<>();
        response.getItems().forEach((k, v) -> {
            body.put(k, v.getValue());
        });

        message.setBody(body);
        message.setHeader(DaprConstants.SUBSCRIPTION_ID, response.getSubscriptionId());
        message.setHeader(DaprConstants.RAW_CONFIG_RESPONSE, response.getItems());

        return exchange;
    }
}
