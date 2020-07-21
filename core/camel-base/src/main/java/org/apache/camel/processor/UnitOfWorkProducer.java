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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Ensures a {@link Producer} is executed within an {@link org.apache.camel.spi.UnitOfWork}.
 */
public final class UnitOfWorkProducer extends DefaultAsyncProducer {

    private final Producer producer;
    private final AsyncProcessor processor;

    /**
     * The producer which should be executed within an {@link org.apache.camel.spi.UnitOfWork}.
     *
     * @param producer the producer
     */
    public UnitOfWorkProducer(Producer producer) {
        super(producer.getEndpoint());
        this.producer = producer;
        // wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(producer.getEndpoint().getCamelContext(), producer);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(null, producer.getEndpoint().getCamelContext()));
        this.processor = internal;
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(processor);
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    public String toString() {
        return "UnitOfWork(" + producer + ")";
    }
}
