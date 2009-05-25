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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.IsSingleton;
import org.apache.camel.ProducerCallback;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * Cache containing created {@link Producer}.
 *
 * @version $Revision$
 */
public class ProducerCache extends ServiceSupport {
    private static final transient Log LOG = LogFactory.getLog(ProducerCache.class);

    private final Map<String, Producer> producers = new HashMap<String, Producer>();

    // TODO: Consider a pool for non singleton producers to leverage in the doInProducer template

    public synchronized Producer getProducer(Endpoint endpoint) {
        String key = endpoint.getEndpointUri();
        Producer answer = producers.get(key);
        if (answer == null) {
            try {
                answer = endpoint.createProducer();
                answer.start();
            } catch (Exception e) {
                throw new FailedToCreateProducerException(endpoint, e);
            }

            // only add singletons to the cache
            boolean singleton = true;
            if (answer instanceof IsSingleton) {
                singleton = ((IsSingleton)answer).isSingleton();
            }

            if (singleton) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding to producer cache with key: " + endpoint + " for producer: " + answer);
                }
                producers.put(key, answer);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Producer for endpoint: " + key + " is not singleton and thus not added to producer cache");
                }
            }
        }
        return answer;
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    public void send(Endpoint endpoint, Exchange exchange) {
        try {
            sendExchange(endpoint, null, null, exchange);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    /**
     * Sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * @return the exchange
     */
    public Exchange send(Endpoint endpoint, Processor processor) {
        try {
            return sendExchange(endpoint, null, processor, null);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }

    /**
     * Sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * @return the exchange
     */
    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor) {
        try {
            return sendExchange(endpoint, pattern, processor, null);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
    }


    /**
     * Sends an exchange to an endpoint using a supplied callback
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param exchange  the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param pattern   the exchange pattern, can be <tt>null</tt>
     * @param callback  the callback
     * @return the response from the callback
     * @throws Exception if an internal processing error has occurred.
     */
    public <T> T doInProducer(Endpoint endpoint, Exchange exchange, ExchangePattern pattern, ProducerCallback<T> callback) throws Exception {
        // get or create the producer
        Producer producer = getProducer(endpoint);

        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
                return null;
            } else {
                throw new IllegalStateException("No producer, this processor has not been started: " + this);
            }
        }

        try {
            // invoke the callback
            return callback.doInProducer(producer, exchange, pattern);
        } finally {
            // stop non singleton producers as we should not leak resources
            boolean singleton = true;
            if (producer instanceof IsSingleton) {
                singleton = ((IsSingleton)producer).isSingleton();
            }
            if (!singleton) {
                producer.stop();
            }
        }
    }

    protected Exchange sendExchange(final Endpoint endpoint, ExchangePattern pattern,
                                    final Processor processor, Exchange exchange) throws Exception {
        return doInProducer(endpoint, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                if (exchange == null) {
                    exchange = pattern != null ? producer.createExchange(pattern) : producer.createExchange();
                }

                if (processor != null) {
                    // lets populate using the processor callback
                    processor.process(exchange);
                }

                // now lets dispatch
                if (LOG.isDebugEnabled()) {
                    LOG.debug(">>>> " + endpoint + " " + exchange);
                }
                producer.process(exchange);
                return exchange;
            }
        });
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producers.values());
        producers.clear();
    }

    protected void doStart() throws Exception {
    }
}
