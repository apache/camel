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
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;
import rx.Observable;
import rx.Subscriber;

public class CamelOperator implements Observable.Operator<Message, Message> {

    private Producer producer;

    public CamelOperator(CamelContext context, String uri) throws Exception {
        this(context.getEndpoint(uri));
    }

    public CamelOperator(Endpoint endpoint) throws Exception {
        this.producer = endpoint.createProducer();
        ServiceHelper.startService(producer);
    }

    @Override
    public Subscriber<? super Message> call(final Subscriber<? super Message> s) {
        return new Subscriber<Message>(s) {
            @Override
            public void onCompleted() {
                try {
                    ServiceHelper.stopService(producer);
                } catch (Exception e) {
                    throw new RuntimeCamelRxException(e);
                } finally {
                    producer = null;
                }
                if (!s.isUnsubscribed()) {
                    s.onCompleted();
                }
            }

            @Override
            public void onError(Throwable e) {
                Exchange exchange = producer.createExchange();
                exchange.setException(e);
                process(exchange);
                if (!s.isUnsubscribed()) {
                    s.onError(e);
                }
            }

            @Override
            public void onNext(Message item) {
                if (!s.isUnsubscribed()) {
                    s.onNext(process(item));
                }
            }
        };
    }

    private Exchange process(Exchange exchange) {
        try {
            producer.process(exchange);
            if (exchange.hasOut()) {
                exchange.setIn(exchange.getOut());
                exchange.setOut(null);
            }
        } catch (Exception e) {
            throw new RuntimeCamelRxException(e);
        }
        return exchange;
    }

    private Message process(Message message) {
        Exchange exchange = producer.createExchange();
        exchange.setIn(message);
        return process(exchange).getIn();
    }
}
