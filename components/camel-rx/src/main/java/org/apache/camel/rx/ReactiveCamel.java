/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.rx.support.EndpointSubscription;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.functions.Func1;

/**
 * Provides the <a href="https://rx.codeplex.com/">Reactive Extensions</a> support for
 * Camel via the <a href="https://github.com/Netflix/RxJava#readme">RxJava library</a>
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
        return toObservable(camelContext.getEndpoint(uri));
    }

    /**
     * Returns an {@link rx.Observable < org.apache.camel.Message >} to allow the messages sent on the endpoint
     * to be processed using  <a href="https://rx.codeplex.com/">Reactive Extensions</a>
     */
    public Observable<Message> toObservable(Endpoint endpoint) {
        return createEndpointObservable(endpoint, new Func1<Exchange, Message>() {
            @Override
            public Message call(Exchange exchange) {
                return exchange.getIn();
            }
        });
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }


    /**
     * Returns a newly created {@link Observable} given a function which converts
     * the {@link Exchange} from the Camel consumer to the required type
     */
    protected <T> Observable<T> createEndpointObservable(final Endpoint endpoint,
                                                         final Func1<Exchange, T> converter) {
        Func1<Observer<Message>, Subscription> func = new Func1<Observer<Message>, Subscription>() {
            @Override
            public Subscription call(Observer<Message> observer) {
                return new EndpointSubscription(endpoint, observer, converter);
            }
        };
        return new EndpointObservable(endpoint, func);
    }
}
