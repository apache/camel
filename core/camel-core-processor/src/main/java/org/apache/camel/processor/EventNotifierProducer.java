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
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.SendHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a {@link Producer} do send {@link org.apache.camel.spi.EventNotifier} notifications when sending.
 */
public final class EventNotifierProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventNotifierProducer.class);

    private final AsyncProducer producer;

    /**
     * The producer which should be executed and emit {@link org.apache.camel.spi.EventNotifier} notifications.
     *
     * @param producer the producer
     */
    public EventNotifierProducer(Producer producer) {
        super(producer.getEndpoint());
        this.producer = AsyncProcessorConverterHelper.convert(producer);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("Producer has not been started: " + this));
            callback.done(true);
            return true;
        }

        SendCallback send = new SendCallback(callback);
        try {
            LOG.debug(">>>> {} {}", getEndpoint(), exchange);
            // emit the sending and sent events around the send (and run the send hook if any)
            return SendHelper.send(exchange, getEndpoint(), producer, send);
        } catch (Exception throwable) {
            if (!send.compareAndSet(false, true)) {
                // the producer called the callback and then threw: the exchange is already continued (maybe on another
                // thread), so it is not completed a second time and the exception is not set on it
                LOG.warn("The producer of {} threw an exception after it completed the exchange. This exception is ignored.",
                        getEndpoint(), throwable);
                return send.doneSync;
            }
            exchange.setException(throwable);
            callback.done(true);
        }

        return true;
    }

    /**
     * The callback of one send: calls the callback of the caller, and records that it did, so that an exception the
     * producer throws after it called the callback does not complete the exchange a second time. A further call of the
     * callback by the producer is passed on as before.
     */
    private final class SendCallback extends AtomicBoolean implements AsyncCallback {
        private final AsyncCallback callback;
        private volatile boolean doneSync;

        private SendCallback(AsyncCallback callback) {
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            if (compareAndSet(false, true)) {
                this.doneSync = doneSync;
            } else {
                LOG.warn("The producer of {} completed the exchange after the send was completed (more than once, or after"
                         + " it threw).",
                        getEndpoint());
            }
            callback.done(doneSync);
        }
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(producer);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(producer);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
    }
}
