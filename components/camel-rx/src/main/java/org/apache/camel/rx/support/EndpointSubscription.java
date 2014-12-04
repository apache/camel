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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;
import rx.Subscription;
import rx.functions.Func1;

/**
 * An RX {@link Subscription} on a Camel {@link Endpoint}
 */
public class EndpointSubscription<T> implements Subscription {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointSubscription.class);

    private final Endpoint endpoint;
    private final Observer<? super T> observer;
    private Consumer consumer;
    private final AtomicBoolean unsubscribed = new AtomicBoolean(false);

    public EndpointSubscription(Endpoint endpoint, final Observer<? super T> observer,
                                final Func1<Exchange, T> func) {
        this.endpoint = endpoint;
        this.observer = observer;

        // lets create the consumer
        Processor processor = new ProcessorToObserver<T>(func, observer);
        try {
            this.consumer = endpoint.createConsumer(processor);
            ServiceHelper.startService(consumer);
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
                try {
                    ServiceHelper.stopServices(consumer);
                } catch (Exception e) {
                    LOG.warn("Error stopping consumer: " + consumer + " due " + e.getMessage() + ". This exception is ignored.", e);
                }
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

}
