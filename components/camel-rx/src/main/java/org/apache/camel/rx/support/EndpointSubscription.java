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
package org.apache.camel.rx.support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;
import rx.Subscription;
import rx.functions.Func1;

/**
 * An RX {@link Subscription} on a Camel {@link Endpoint}
 */
public class EndpointSubscription<T> extends ServiceSupport implements Subscription {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointSubscription.class);

    private final ExecutorService workerPool;
    private final Endpoint endpoint;
    private final Observer<? super T> observer;
    private Consumer consumer;
    private final AtomicBoolean unsubscribed = new AtomicBoolean(false);

    public EndpointSubscription(ExecutorService workerPool, Endpoint endpoint, final Observer<? super T> observer,
                                final Func1<Exchange, T> func) {
        this.workerPool = workerPool;
        this.endpoint = endpoint;
        this.observer = observer;

        // lets create the consumer
        Processor processor = new ProcessorToObserver<T>(func, observer);
        // must ensure the consumer is being executed in an unit of work so synchronization callbacks etc is invoked
        CamelInternalProcessor internal = new CamelInternalProcessor(processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(null));
        try {
            // need to start endpoint before we create producer
            ServiceHelper.startService(endpoint);
            this.consumer = endpoint.createConsumer(internal);
            // add as service so we ensure it gets stopped when CamelContext stops
            endpoint.getCamelContext().addService(consumer, true, true);
        } catch (Exception e) {
            observer.onError(e);
        }
    }

    @Override
    public String toString() {
        return "EndpointSubscription[" + endpoint + " observer: " + observer + "]";
    }

    @Override
    public void unsubscribe() {
        if (unsubscribed.compareAndSet(false, true)) {
            if (consumer != null) {
                // must stop the consumer from the worker pool as we should not stop ourself from a thread from ourself
                workerPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ServiceHelper.stopServices(consumer);
                        } catch (Exception e) {
                            LOG.warn("Error stopping consumer: " + consumer + " due " + e.getMessage() + ". This exception is ignored.", e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public boolean isUnsubscribed() {
        return unsubscribed.get();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Observer<? super T> getObserver() {
        return observer;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(consumer);
        unsubscribed.set(false);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(consumer);
        unsubscribed.set(true);
    }
}
