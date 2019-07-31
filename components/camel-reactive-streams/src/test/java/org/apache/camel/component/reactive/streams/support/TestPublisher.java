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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A publisher for tests.
 */
public class TestPublisher<T> implements Publisher<T> {

    private Iterable<T> data;

    private long delay;

    public TestPublisher(Iterable<T> data) {
        this(data, 0L);
    }

    public TestPublisher(Iterable<T> data, long delay) {
        this.data = data;
        this.delay = delay;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Subscription() {

            private Iterator<T> it = data.iterator();

            private AtomicLong requested = new AtomicLong(0);

            private Object monitor = new Object();

            @Override
            public void request(long l) {
                this.requested.addAndGet(l);

                new Thread() {
                    @Override
                    public void run() {
                        synchronized (monitor) {
                            boolean wasNonEmpty = it.hasNext();
                            while (requested.longValue() > 0 && it.hasNext()) {
                                T d = it.next();
                                requested.decrementAndGet();

                                if (delay > 0) {
                                    try {
                                        Thread.sleep(delay);
                                    } catch (InterruptedException ex) {
                                    }
                                }
                                subscriber.onNext(d);
                            }

                            if (wasNonEmpty && !it.hasNext()) {
                                // data cannot be added to this test publisher
                                subscriber.onComplete();
                            }
                        }
                    }
                }.start();
            }

            @Override
            public void cancel() {
                synchronized (monitor) {
                    this.requested.set(0);
                    this.it = new Iterator<T>() {
                        @Override
                        public boolean hasNext() {
                            return false;
                        }

                        @Override
                        public T next() {
                            throw new NoSuchElementException();
                        }
                    };
                    new Thread() {
                        public void run() {
                            subscriber.onComplete();
                        }
                    }.start();
                }
            }
        });
    }

}
