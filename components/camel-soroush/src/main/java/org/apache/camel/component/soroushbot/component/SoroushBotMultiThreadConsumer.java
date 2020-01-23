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
package org.apache.camel.component.soroushbot.component;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.utils.CongestionException;
import org.apache.camel.component.soroushbot.utils.MultiQueueWithTopicThreadPool;

/**
 * create a thread pool and process each message using one of threads,
 * it is guaranteed that all message from a same person will processed by the same thread.
 * thread pool size could be configured using {@link SoroushBotEndpoint#getConcurrentConsumers()}
 * this consumer support both Sync and Async processors.
 */
//CHECKSTYLE:OFF
public class SoroushBotMultiThreadConsumer extends SoroushBotAbstractConsumer {

    /**
     * Since we want that every message from the same user to be processed one by one,
     * i.e. no 2 message from the same user execute concurrently,
     * we create a new simple thread pool that let us select a thread by a topic.
     * It guarantees that all tasks with the same topic execute in the same thread.
     * We use userIds as the topic of each task.
     */
    MultiQueueWithTopicThreadPool threadPool;

    public SoroushBotMultiThreadConsumer(SoroushBotEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        threadPool = new MultiQueueWithTopicThreadPool(endpoint.getConcurrentConsumers(), endpoint.getQueueCapacityPerThread(), "Soroush Thread");
    }

    @Override
    protected void sendExchange(Exchange exchange) {
        try {
            threadPool.execute(exchange.getIn().getBody(SoroushMessage.class).getFrom(), () -> {
                try {
                    if (endpoint.isSynchronous()) {
                        getProcessor().process(exchange);
                    } else {
                        getAsyncProcessor().process(exchange, doneSync -> {});
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                }
            });
        } catch (IllegalStateException ex) {
            throw new CongestionException(ex, exchange.getIn().getBody(SoroushMessage.class));
        }

        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange",
                    exchange, exchange.getException());
        }
    }
}
