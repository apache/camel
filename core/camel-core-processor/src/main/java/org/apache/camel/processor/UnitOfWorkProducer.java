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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a {@link Producer} is executed within an {@link org.apache.camel.spi.UnitOfWork}.
 */
public final class UnitOfWorkProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(UnitOfWorkProducer.class);

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
        // wrap in unit of work; the producer is called through a ProducerSend, so the unit of work is done also when
        // the producer throws before it called the callback
        CamelContext ecc = producer.getEndpoint().getCamelContext();
        this.processor = PluginHelper.getInternalProcessorFactory(ecc)
                .addUnitOfWorkProcessorAdvice(ecc, new ProducerSend(producer), null);
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
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(processor);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(processor);
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

    /**
     * Calls the producer within the unit of work. A producer that throws before it called the callback does not keep
     * the async contract: the exception is set on the exchange and the callback is called, so the unit of work is done
     * and the exchange does not stay in the inflight repository. An exception the producer throws after it called the
     * callback is thrown on as it is, as before: the caller, which knows that its callback was called, handles it. A
     * further call of the callback is ignored, as the unit of work is done once. As for a processor (CAMEL-26741), an
     * {@link Error} is handled as an exception, and a {@link VirtualMachineError} is thrown on as before.
     */
    private static final class ProducerSend extends DelegateAsyncProcessor {

        private final Producer producer;

        private ProducerSend(Producer producer) {
            super(producer);
            this.producer = producer;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            SendCallback send = new SendCallback(producer, callback);
            try {
                return processor.process(exchange, send);
            } catch (VirtualMachineError e) {
                throw e;
            } catch (RuntimeException | Error e) {
                if (!send.compareAndSet(false, true)) {
                    // the producer called the callback and then threw: the unit of work is already done
                    throw e;
                }
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        @Override
        public String toString() {
            return producer.toString();
        }
    }

    /**
     * The callback of one send: calls the callback of the unit of work once, and records that it did.
     */
    private static final class SendCallback extends AtomicBoolean implements AsyncCallback {

        private final Producer producer;
        private final AsyncCallback callback;

        private SendCallback(Producer producer, AsyncCallback callback) {
            this.producer = producer;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            if (compareAndSet(false, true)) {
                callback.done(doneSync);
            } else {
                // the callback runs the after advices, such as the end of the unit of work, which must happen once
                LOG.warn("The producer of {} completed the exchange after the send was completed (more than once, or after"
                         + " it threw). This is ignored.",
                        producer.getEndpoint());
            }
        }
    }
}
