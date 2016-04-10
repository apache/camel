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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.processor.UnitOfWorkProducer;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

/**
 * An {@link Observer} which sends events to a given {@link Endpoint}
 */
public class ObserverSender<T> extends ServiceSupport implements Observer<T> {
    private static final Logger LOG = LoggerFactory.getLogger(ObserverSender.class);

    private Producer producer;

    public ObserverSender(Endpoint endpoint) throws Exception {
        // need to start endpoint before we create producer
        ServiceHelper.startService(endpoint);
        this.producer = new UnitOfWorkProducer(endpoint.createProducer());
        // add as service so we ensure it gets stopped when CamelContext stops
        endpoint.getCamelContext().addService(producer, true, true);
    }

    @Override
    public void onCompleted() {
        if (producer != null) {
            try {
                ServiceHelper.stopService(producer);
            } catch (Exception e) {
                LOG.warn("Error stopping producer: " + producer + " due " + e.getMessage() + ". This exception is ignored.", e);
            } finally {
                producer = null;
            }
        }
    }

    @Override
    public void onError(Throwable e) {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.setException(e);
        send(exchange);
    }

    @Override
    public void onNext(T o) {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getIn().setBody(o);
        send(exchange);
    }

    protected void send(Exchange exchange) {
        try {
            producer.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
    }
}
