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
package org.apache.camel.rx;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.rx.support.EndpointObservable;
import org.apache.camel.rx.support.EndpointSubscribeFunc;
import org.apache.camel.rx.support.ExchangeToBodyFunc1;
import org.apache.camel.rx.support.ExchangeToMessageFunc1;
import org.apache.camel.rx.support.ObserverSender;
import org.apache.camel.util.CamelContextHelper;
import rx.Observable;
import rx.functions.Func1;

/**
 * Provides the <a href="https://rx.codeplex.com/">Reactive Extensions</a> support for
 * Camel via the <a href="https://github.com/Netflix/RxJava/wiki">RxJava library</a>
 */
public class ReactiveCamel {
    private final CamelContext camelContext;

    public ReactiveCamel(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Returns an {@link rx.Observable < org.apache.camel.Message >} to allow the messages sent on the endpoint
     * to be processed using  <a href="https://rx.codeplex.com/">Reactive Extensions</a>
     */
    public Observable<Message> toObservable(String uri) {
        return toObservable(endpoint(uri));
    }

    /**
     * Returns an {@link rx.Observable <T>} for the messages with their payload converted to the given type
     * to allow the messages sent on the endpoint
     * to be processed using  <a href="https://rx.codeplex.com/">Reactive Extensions</a>
     */
    public <T> Observable<T> toObservable(String uri, final Class<T> bodyType) {
        return toObservable(endpoint(uri), bodyType);
    }

    /**
     * Returns an {@link rx.Observable < org.apache.camel.Message >} to allow the messages sent on the endpoint
     * to be processed using  <a href="https://rx.codeplex.com/">Reactive Extensions</a>
     */
    public Observable<Message> toObservable(Endpoint endpoint) {
        return createEndpointObservable(endpoint, ExchangeToMessageFunc1.getInstance());
    }

    /**
     * Returns an {@link rx.Observable <T>} for the messages with their payload converted to the given type
     * to allow the messages sent on the endpoint
     * to be processed using  <a href="https://rx.codeplex.com/">Reactive Extensions</a>
     */
    public <T> Observable<T> toObservable(Endpoint endpoint, final Class<T> bodyType) {
        return createEndpointObservable(endpoint, new ExchangeToBodyFunc1<T>(bodyType));
    }

    /**
     * Sends events on the given {@link Observable} to the given camel endpoint
     */
    public <T> void sendTo(Observable<T> observable, String endpointUri) {
        sendTo(observable, endpoint(endpointUri));
    }
    /**
     * Sends events on the given {@link Observable} to the given camel endpoint
     */
    public <T> void sendTo(Observable<T> observable, Endpoint endpoint) {
        try {
            ObserverSender<T> observer = new ObserverSender<T>(endpoint);
            observable.subscribe(observer);
        } catch (Exception e) {
            throw new RuntimeCamelRxException(e);
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Endpoint endpoint(String endpointUri) {
        return CamelContextHelper.getMandatoryEndpoint(camelContext, endpointUri);
    }

    /**
     * Returns a newly created {@link Observable} given a function which converts
     * the {@link Exchange} from the Camel consumer to the required type
     */
    protected <T> Observable<T> createEndpointObservable(final Endpoint endpoint,
                                                         final Func1<Exchange, T> converter) {
        Observable.OnSubscribe<T> func = new EndpointSubscribeFunc<T>(endpoint, converter);
        return new EndpointObservable<T>(endpoint, func);
    }

}
