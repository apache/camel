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

import com.hazelcast.core.Transaction;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.util.AsyncProcessorConverterHelper;
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
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    protected void doStart() throws Exception {
        int concurrentConsumers = endpoint.getConfiguration().getConcurrentConsumers();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), concurrentConsumers);
        for (int i = 0; i < concurrentConsumers; i++) {
            executor.execute(this);
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        super.doStop();
    }

    public void run() {
        final BlockingQueue<?> queue = endpoint.getQueue();

        while (queue != null && isRunAllowed()) {
            final Exchange exchange = this.getEndpoint().createExchange();

            Transaction transaction = null;
            if (endpoint.getConfiguration().isTransacted()) {
                // Get and begin transaction if exist
                transaction = endpoint.getHazelcastInstance().getTransaction();

                if (transaction != null && transaction.getStatus() == Transaction.TXN_STATUS_NO_TXN) {
                    log.trace("Begin transaction: {}", transaction);
                    transaction.begin();
                }
            }
            try {
                final Object body = queue.poll(endpoint.getConfiguration().getPollInterval(), TimeUnit.MILLISECONDS);

                if (body != null) {
                    if (body instanceof DefaultExchangeHolder) {
                        DefaultExchangeHolder.unmarshal(exchange, (DefaultExchangeHolder) body);
                    } else {
                        exchange.getIn().setBody(body);
                    }
                    try {
                        // process using the asynchronous routing engine
                        processor.process(exchange, new AsyncCallback() {
                            public void done(boolean asyncDone) {
                                // noop
                            }
                        });

                        if (exchange.getException() != null) {
                            // Rollback
                            if (transaction != null) {
                                transaction.rollback();
                            }
                            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                        }

                    } catch (Exception e) {
                        LOG.error("Hzlq Exception caught: " + e, e);
                        // Rollback
                        if (transaction != null) {
                            log.trace("Rollback transaction: {}", transaction);
                            transaction.rollback();
                        }
                    }
                }
                // It's OK, I commit
                if (exchange.getException() == null && transaction != null && transaction.getStatus() == Transaction.TXN_STATUS_ACTIVE) {
                    log.trace("Commit transaction: {}", transaction);
                    transaction.commit();
                }
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Hzlq Consumer Interrupted: " + e, e);
                }
                continue;
            } catch (Throwable e) {
                // Rollback
                if (transaction != null) {
                    log.trace("Rollback transaction: {}", transaction);
                    transaction.rollback();
                }
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }
    }

}
