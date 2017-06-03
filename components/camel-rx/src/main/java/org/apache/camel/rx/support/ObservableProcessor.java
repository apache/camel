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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ServiceSupport;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * A base class for implementing a {@link Processor} which provides access to an {@link Observable}
 * so that the messages can be processed using the <a href="https://github.com/Netflix/RxJava/wiki">RX Java API</a>
 */
public abstract class ObservableProcessor<T> extends ServiceSupport implements Processor {
    private final Subject<T, T> observable = PublishSubject.create();
    private final ProcessorToObserver<T> processor;

    protected ObservableProcessor(Func1<Exchange, T> func) {
        this.processor = new ProcessorToObserver<T>(func, observable);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        processor.process(exchange);
    }

    /**
     * Returns the {@link Observable} for this {@link Processor} so that the messages that are received
     * can be processed using the <a href="https://github.com/Netflix/RxJava/wiki">RX Java API</a>
     */
    public Observable<T> getObservable() {
        return observable;
    }

    /**
     * Provides the configuration hook so that derived classes can process the observable
     * to use whatever RX methods they wish to process the incoming events
     */
    protected abstract void configure(Observable<T> observable);

    @Override
    protected void doStart() throws Exception {
        configure(getObservable());
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
