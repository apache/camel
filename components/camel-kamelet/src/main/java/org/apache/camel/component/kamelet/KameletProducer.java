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
package org.apache.camel.component.kamelet;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KameletProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KameletProducer.class);

    private volatile KameletConsumer consumer;
    private int stateCounter;

    private final KameletEndpoint endpoint;
    private final KameletComponent component;
    private final String key;
    private final boolean block;
    private final long timeout;
    private final boolean sink;

    public KameletProducer(KameletEndpoint endpoint, String key) {
        super(endpoint);
        this.endpoint = endpoint;
        this.component = endpoint.getComponent();
        this.key = key;
        this.block = endpoint.isBlock();
        this.timeout = endpoint.getTimeout();
        this.sink = getEndpoint().getEndpointKey().startsWith("kamelet://sink");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (consumer == null || stateCounter != component.getStateCounter()) {
            stateCounter = component.getStateCounter();
            consumer = component.getConsumer(key, block, timeout);
        }
        if (consumer == null) {
            if (endpoint.isFailIfNoConsumers()) {
                throw new KameletConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange);
            } else {
                LOG.debug("message ignored, no consumers available on endpoint: {}", endpoint);
            }
        } else {
            consumer.getProcessor().process(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            if (consumer == null || stateCounter != component.getStateCounter()) {
                stateCounter = component.getStateCounter();
                consumer = component.getConsumer(key, block, timeout);
            }
            if (consumer == null) {
                if (endpoint.isFailIfNoConsumers()) {
                    exchange.setException(new KameletConsumerNotAvailableException(
                            "No consumers available on endpoint: " + endpoint, exchange));
                } else {
                    LOG.debug("Exchange ignored, no consumers available on endpoint: {}", endpoint);
                }
                callback.done(true);
                return true;
            } else {
                // the kamelet producer has multiple purposes at this point
                // it is capable of linking the kamelet component with the kamelet EIP
                // to ensure the EIP and the component are wired together with their
                // kamelet:source and kamelet:sink endpoints so when calling the sink
                // then we continue processing the EIP child processors

                // if no EIP is in use, then its _just_ a regular camel component
                // with producer and consumers linked together via the component

                if (sink) {
                    // when calling a kamelet:sink then lookup any waiting processor
                    // from the Kamelet EIP to continue routing
                    AsyncProcessor eip = (AsyncProcessor) component.getKameletEip(key);
                    if (eip != null) {
                        return eip.process(exchange, callback);
                    } else {
                        // if the current route is from a kamelet source then we should
                        // break out as otherwise we would end up calling ourselves again
                        Route route = ExchangeHelper.getRoute(exchange);
                        boolean source = route != null && route.getConsumer() instanceof KameletConsumer;
                        if (source) {
                            callback.done(true);
                            return true;
                        }
                    }
                }
                // kamelet producer that calls its kamelet consumer to process the incoming exchange
                return consumer.getAsyncProcessor().process(exchange, callback);
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    public String getKey() {
        return key;
    }

}
