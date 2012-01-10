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
package org.apache.camel.component.hazelcast.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Hazelcast SEDA {@link Consumer} component.
 */
public class HazelcastSedaConsumer extends DefaultConsumer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastSedaConsumer.class);
    private final HazelcastSedaEndpoint endpoint;
    private final AsyncProcessor processor;
    private ExecutorService executor;

    public HazelcastSedaConsumer(final Endpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = (HazelcastSedaEndpoint) endpoint;
        this.processor = AsyncProcessorTypeConverter.convert(processor);
    }

    @Override
    protected void doStart() throws Exception {
        int concurrentConsumers = endpoint.getConfiguration().getConcurrentConsumers();
        executor = endpoint.getCamelContext().getExecutorServiceStrategy().newFixedThreadPool(this, endpoint.getEndpointUri(), concurrentConsumers);
        for (int i = 0; i < concurrentConsumers; i++) {
            executor.execute(this);
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceStrategy().shutdownNow(executor);
            executor = null;
        }
        super.doStop();
    }

    public void run() {
        final BlockingQueue queue = endpoint.getQueue();

        while (queue != null && isRunAllowed()) {
            final Exchange exchange = new DefaultExchange(this.getEndpoint().getCamelContext());

            try {
                final Object body = queue.poll(endpoint.getConfiguration().getPollInterval(), TimeUnit.MILLISECONDS);

                if (body != null) {
                    exchange.getIn().setBody(body);
                    try {
                        processor.process(exchange, new AsyncCallback() {
                            public void done(final boolean sync) {
                            }
                        });

                        if (exchange.getException() != null) {
                            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                        }

                    } catch (Exception e) {
                        LOG.error("Hzlq Exception caught: " + e, e);
                    }
                }
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Hzlq Consumer Interrupted: " + e, e);
                }
                continue;
            } catch (Throwable e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }
    }

}
