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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.apache.camel.util.ServiceHelper;
import rx.Observer;
import rx.Subscription;
import rx.util.functions.Func1;

/**
 * An RX {@link Subscription} on a Camel {@link Endpoint}
 */
public class EndpointSubscription<T> implements Subscription {
    private final Endpoint endpoint;
    private final Observer<T> observer;
    private Consumer consumer;

    public EndpointSubscription(Endpoint endpoint, final Observer<T> observer,
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
        if (consumer != null) {
            try {
                ServiceHelper.stopServices(consumer);

                // TODO should this fire the observer.onComplete()?
                observer.onCompleted();
            } catch (Exception e) {
                observer.onError(e);
            }
        }
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Observer<T> getObserver() {
        return observer;
    }

}
