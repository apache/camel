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
package org.apache.camel.component.reactive.streams.support;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A subscriber for tests.
 */
public class TestSubscriber<T> implements Subscriber<T> {

    protected Subscription subscription;

    private long initiallyRequested;

    public TestSubscriber() {
    }

    public long getInitiallyRequested() {
        return initiallyRequested;
    }

    public void setInitiallyRequested(long initiallyRequested) {
        this.initiallyRequested = initiallyRequested;
    }

    public void request(long exchanges) {
        this.subscription.request(exchanges);
    }

    public void cancel() {
        this.subscription.cancel();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;

        if (initiallyRequested > 0) {
            subscription.request(initiallyRequested);
        }
    }

    @Override
    public void onNext(T t) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}
