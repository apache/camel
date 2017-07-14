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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

public class EndpointSubscribeFunc<T> implements Observable.OnSubscribe<T> {

    private final ExecutorService workerPool;
    private final Endpoint endpoint;
    private final Func1<Exchange, T> converter;

    public EndpointSubscribeFunc(ExecutorService workerPool, Endpoint endpoint, Func1<Exchange, T> converter) {
        this.workerPool = workerPool;
        this.endpoint = endpoint;
        this.converter = converter;
    }

    @Override
    public void call(Subscriber<? super T> subscriber) {
        subscriber.add(new EndpointSubscription<T>(workerPool, endpoint, subscriber, converter));
    }
}
