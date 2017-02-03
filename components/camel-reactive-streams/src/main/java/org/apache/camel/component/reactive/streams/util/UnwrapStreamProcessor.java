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
package org.apache.camel.component.reactive.streams.util;

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.util.AsyncProcessorHelper;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A Processor that converts a Publisher into its content asynchronously.
 */
public class UnwrapStreamProcessor implements AsyncProcessor {

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Object content = exchange.getIn().getBody();
        if (content instanceof Publisher) {
            Publisher<?> pub = Publisher.class.cast(content);

            List<Object> data = new LinkedList<>();

            pub.subscribe(new Subscriber<Object>() {

                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Object o) {
                    data.add(o);
                }

                @Override
                public void onError(Throwable throwable) {
                    addData();
                    exchange.setException(throwable);
                    callback.done(false);
                }

                @Override
                public void onComplete() {
                    addData();
                    callback.done(false);
                }

                private void addData() {
                    Object body;
                    if (data.size() == 0) {
                        body = null;
                    } else if (data.size() == 1) {
                        body = data.get(0);
                    } else {
                        body = data;
                    }

                    if (body instanceof Exchange && !exchange.equals(body)) {
                        // copy into the original Exchange
                        Exchange copy = (Exchange) body;
                        exchange.setException(copy.getException());
                        exchange.setIn(copy.getIn());
                        exchange.setOut(copy.getOut());
                        exchange.getProperties().clear();
                        exchange.getProperties().putAll(copy.getProperties());
                    } else {
                        exchange.getOut().setBody(body);
                    }
                }

            });

            return false;
        }

        callback.done(true);
        return true;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

}
