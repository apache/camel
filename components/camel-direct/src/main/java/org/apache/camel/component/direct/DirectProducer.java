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
package org.apache.camel.component.direct;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The direct producer.
 */
public class DirectProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DirectProducer.class);

    private volatile DirectConsumer consumer;
    private int stateCounter;

    private final DirectEndpoint endpoint;
    private final DirectComponent component;
    private final String key;
    private final boolean block;
    private final long timeout;

    public DirectProducer(DirectEndpoint endpoint, String key) {
        super(endpoint);
        this.endpoint = endpoint;
        this.component = (DirectComponent) endpoint.getComponent();
        this.key = key;
        this.block = endpoint.isBlock();
        this.timeout = endpoint.getTimeout();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (consumer == null || stateCounter != component.getStateCounter()) {
            stateCounter = component.getStateCounter();
            consumer = component.getConsumer(key, block, timeout);
        }
        if (consumer == null) {
            if (endpoint.isFailIfNoConsumers()) {
                throw new DirectConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange);
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
            // we may be forced synchronous
            if (endpoint.isSynchronous()) {
                process(exchange);
                callback.done(true);
                return true;
            }
            if (consumer == null || stateCounter != component.getStateCounter()) {
                stateCounter = component.getStateCounter();
                consumer = component.getConsumer(key, block, timeout);
            }
            if (consumer == null) {
                if (endpoint.isFailIfNoConsumers()) {
                    exchange.setException(new DirectConsumerNotAvailableException(
                            "No consumers available on endpoint: " + endpoint, exchange));
                } else {
                    LOG.debug("message ignored, no consumers available on endpoint: {}", endpoint);
                }
                callback.done(true);
                return true;
            } else {
                // the consumer may be forced synchronous
                if (consumer.getEndpoint().isSynchronous()) {
                    consumer.getProcessor().process(exchange);
                    callback.done(true);
                    return true;
                } else {
                    return consumer.getAsyncProcessor().process(exchange, callback);
                }
            }
        } catch (InterruptedException e) {
            LOG.info("Interrupted while processing the exchange");
            Thread.currentThread().interrupt();

            exchange.setException(e);
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

}
