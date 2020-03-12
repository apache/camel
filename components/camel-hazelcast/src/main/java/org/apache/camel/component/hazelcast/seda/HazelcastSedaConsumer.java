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
package org.apache.camel.component.hazelcast.seda;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.hazelcast.collection.BaseQueue;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchangeHolder;
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
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
        super.doStop();
    }

    @Override
    public void run() {
        BaseQueue<?> queue = endpoint.getHazelcastInstance().getQueue(endpoint.getConfiguration().getQueueName());

        while (queue != null && isRunAllowed()) {
            final Exchange exchange = this.getEndpoint().createExchange();

            TransactionContext transactionCtx = null;
            try {
                if (endpoint.getConfiguration().isTransacted()) {
                    // Get and begin transaction if exist
                    transactionCtx = endpoint.getHazelcastInstance().newTransactionContext();

                    if (transactionCtx != null) {
                        LOG.trace("Begin transaction: {}", transactionCtx.getTxnId());
                        transactionCtx.beginTransaction();
                        queue = transactionCtx.getQueue(endpoint.getConfiguration().getQueueName());
                    }
                }

                final Object body = queue.poll(endpoint.getConfiguration().getPollTimeout(), TimeUnit.MILLISECONDS);

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
                            if (transactionCtx != null) {
                                transactionCtx.rollbackTransaction();
                            }
                            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                        }

                    } catch (Exception e) {
                        LOG.error("Hzlq Exception caught: {}", e, e);
                        // Rollback
                        if (transactionCtx != null) {
                            LOG.trace("Rollback transaction: {}", transactionCtx.getTxnId());
                            transactionCtx.rollbackTransaction();
                        }
                    }
                }
                // It's OK, I commit
                if (exchange.getException() == null && transactionCtx != null) {
                    LOG.trace("Commit transaction: {}", transactionCtx.getTxnId());
                    transactionCtx.commitTransaction();
                }
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Hzlq Consumer Interrupted: {}", e, e);
                }
                continue;
            } catch (Throwable e) {
                // Rollback
                if (transactionCtx != null) {
                    LOG.trace("Rollback transaction: {}", transactionCtx.getTxnId());
                    try {
                        transactionCtx.rollbackTransaction();
                    } catch (Throwable ignore) {
                    }
                }
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
                try {
                    Thread.sleep(endpoint.getConfiguration().getOnErrorDelay());
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

}
