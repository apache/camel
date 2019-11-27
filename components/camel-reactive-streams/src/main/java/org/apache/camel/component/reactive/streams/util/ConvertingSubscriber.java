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
package org.apache.camel.component.reactive.streams.util;

import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A subscriber that converts items of the given type into Camel {@code Exchange}s.
 */
public class ConvertingSubscriber<R> implements Subscriber<R> {

    private Class<R> type;

    private Subscriber<Exchange> delegate;

    private CamelContext context;

    public ConvertingSubscriber(Subscriber<Exchange> delegate, CamelContext context, Class<R> type) {
        Objects.requireNonNull(delegate, "delegate subscriber cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        this.delegate = delegate;
        this.context = context;
        this.type = type;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (subscription == null) {
            throw new NullPointerException("subscription is null");
        }

        delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(R r) {
        if (r == null) {
            throw new NullPointerException("element is null");
        }

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(r, type);
        delegate.onNext(exchange);
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable == null) {
            throw new NullPointerException("throwable is null");
        }

        delegate.onError(throwable);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }
}
