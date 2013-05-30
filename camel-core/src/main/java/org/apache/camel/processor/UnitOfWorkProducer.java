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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a {@link Producer} is executed within an {@link org.apache.camel.spi.UnitOfWork}.
 *
 * @version 
 */
public final class UnitOfWorkProducer implements Producer {

    private static final transient Logger LOG = LoggerFactory.getLogger(UnitOfWorkProducer.class);
    private final Producer producer;
    private final AsyncProcessor processor;

    /**
     * The producer which should be executed within an {@link org.apache.camel.spi.UnitOfWork}.
     *
     * @param producer the producer
     */
    public UnitOfWorkProducer(Producer producer) {
        this.producer = producer;
        // wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(producer);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(null));
        this.processor = internal;
    }

    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    public Exchange createExchange() {
        return producer.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return producer.createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return producer.createExchange(exchange);
    }

    public void process(final Exchange exchange) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (!doneSync) {
                    LOG.trace("Asynchronous callback received for exchangeId: {}", exchange.getExchangeId());
                    latch.countDown();
                }
            }

            @Override
            public String toString() {
                return "Done " + processor;
            }
        });
        if (!sync) {
            LOG.trace("Waiting for asynchronous callback before continuing for exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
            latch.await();
            LOG.trace("Asynchronous callback received, will continue routing exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
        }
    }

    public void start() throws Exception {
        ServiceHelper.startService(processor);
    }

    public void stop() throws Exception {
        ServiceHelper.stopService(processor);
    }

    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    public String toString() {
        return "UnitOfWork(" + producer + ")";
    }
}
