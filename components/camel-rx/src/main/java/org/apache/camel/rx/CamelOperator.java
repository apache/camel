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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.processor.PipelineHelper;
import org.apache.camel.util.ServiceHelper;
import rx.Observable;
import rx.Subscriber;

public class CamelOperator implements Observable.Operator<Exchange, Exchange> {

    private ProducerTemplate producerTemplate;
    private Endpoint endpoint;

    public CamelOperator(CamelContext context, String uri) throws Exception {
        producerTemplate = context.createProducerTemplate();
        endpoint = context.getEndpoint(uri);
        ServiceHelper.startService(producerTemplate);
    }

    public CamelOperator(Endpoint endpoint) throws Exception {
        this.producerTemplate = endpoint.getCamelContext().createProducerTemplate();
        this.endpoint = endpoint;
        ServiceHelper.startService(producerTemplate);
    }

    @Override
    public Subscriber<? super Exchange> call(final Subscriber<? super Exchange> s) {
        return new Subscriber<Exchange>(s) {
            @Override
            public void onCompleted() {
                try {
                    ServiceHelper.stopService(producerTemplate);
                } catch (Exception e) {
                    throw new RuntimeCamelRxException(e);
                } finally {
                    producerTemplate = null;
                }
                if (!s.isUnsubscribed()) {
                    s.onCompleted();
                }
            }

            @Override
            public void onError(Throwable e) {
                // producer cannot handler the exception
                // so we just pass the exchange to the subscriber 
                if (!s.isUnsubscribed()) {
                    s.onError(e);
                }
            }

            @Override
            public void onNext(Exchange item) {
                if (!s.isUnsubscribed()) {
                    Exchange exchange = process(item);
                    if (exchange.getException() != null) {
                        s.onError(exchange.getException());
                    } else {
                        s.onNext(PipelineHelper.createNextExchange(exchange));
                    }
                }
            }
        };
    }

    private Exchange process(Exchange exchange) {
        try {
            exchange = producerTemplate.send(endpoint, exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        return exchange;
    }
}
