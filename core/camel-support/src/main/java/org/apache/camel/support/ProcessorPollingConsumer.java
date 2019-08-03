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
package org.apache.camel.support;

import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.IsSingleton;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A simple implementation of {@link org.apache.camel.PollingConsumer} which just uses
 * a {@link Processor}. This implementation does not support timeout based
 * receive methods such as {@link #receive(long)}
 */
public class ProcessorPollingConsumer extends PollingConsumerSupport implements IsSingleton {
    private final Processor processor;

    public ProcessorPollingConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint);
        this.processor = processor;
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor);
    }

    @Override
    public Exchange receive() {
        // must be started
        if (!isRunAllowed() || !isStarted()) {
            throw new RejectedExecutionException(this + " is not started, but in state: " + getStatus().name());
        }

        Exchange exchange = getEndpoint().createExchange();
        try {
            processor.process(exchange);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Error while processing exchange", exchange, e);
        }
        return exchange;
    }

    @Override
    public Exchange receiveNoWait() {
        return receive();
    }

    @Override
    public Exchange receive(long timeout) {
        return receive();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
    
}
