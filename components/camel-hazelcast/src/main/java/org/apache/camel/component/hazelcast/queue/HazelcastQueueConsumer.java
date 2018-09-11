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
package org.apache.camel.component.hazelcast.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;
import org.apache.camel.component.hazelcast.listener.CamelItemListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HazelcastQueueConsumer extends HazelcastDefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastQueueConsumer.class);
    private final Processor processor;
    private ExecutorService executor;
    private QueueConsumerTask queueConsumerTask;
    private HazelcastQueueConfiguration config;

    public HazelcastQueueConsumer(HazelcastInstance hazelcastInstance, Endpoint endpoint, Processor processor, String cacheName, final HazelcastQueueConfiguration configuration) {
        super(hazelcastInstance, endpoint, processor, cacheName);
        this.processor = processor;
        this.config = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = ((HazelcastQueueEndpoint)getEndpoint()).createExecutor();

        CamelItemListener camelItemListener = new CamelItemListener(this, cacheName);
        queueConsumerTask = new QueueConsumerTask(camelItemListener);
        executor.submit(queueConsumerTask);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class QueueConsumerTask implements Runnable {

        CamelItemListener camelItemListener;

        public QueueConsumerTask(CamelItemListener camelItemListener) {
            this.camelItemListener = camelItemListener;
        }

        @Override
        public void run() {
            IQueue<Object> queue = hazelcastInstance.getQueue(cacheName);
            if (config.getQueueConsumerMode() == HazelcastQueueConsumerMode.LISTEN) {
                queue.addItemListener(camelItemListener, true);
            }

            if (config.getQueueConsumerMode() == HazelcastQueueConsumerMode.POLL) {
                while (isRunAllowed()) {
                    try {
                        final Object body = queue.poll(config.getPollingTimeout(), TimeUnit.MILLISECONDS);
                        Exchange exchange = getEndpoint().createExchange();
                        exchange.getOut().setBody(body);
                        try {
                            processor.process(exchange);
                        } catch (Exception e) {
                            getExceptionHandler().handleException("Error during processing", exchange, e);
                        }
                    } catch (InterruptedException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Hazelcast Queue Consumer Interrupted: {}", e, e);
                            continue;
                        }
                    }
                }
            }
        }
    }

}
