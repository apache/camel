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

import rx.Observer;
import rx.functions.Func1;

/**
 * A {@link Processor} which invokes an underling {@link Observer} as messages
 * arrive using the given function to convert the {@link Exchange} to the required
 * object
 */
public class ProcessorToObserver<T> implements Processor {
    private final Func1<Exchange, T> func;
    private final Observer<? super T> observer;

    public ProcessorToObserver(Func1<Exchange, T> func, Observer<? super T> observer) {
        this.func = func;
        this.observer = observer;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception exception = null;
        if (exchange.isFailed()) {
            exception = exchange.getException();
        }
        if (exception != null) {
            observer.onError(exception);
        } else {
            T value = func.call(exchange);
            observer.onNext(value);
        }
    }
}
