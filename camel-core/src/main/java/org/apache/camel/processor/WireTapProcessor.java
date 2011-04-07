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
package org.apache.camel.processor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Processor for wire tapping exchanges to an endpoint destination.
 *
 * @version 
 */
public class WireTapProcessor extends SendProcessor {
    private final ExecutorService executorService;

    // expression or processor used for populating a new exchange to send
    // as opposed to traditional wiretap that sends a copy of the original exchange
    private Expression newExchangeExpression;
    private Processor newExchangeProcessor;
    private boolean copy;

    public WireTapProcessor(Endpoint destination, ExecutorService executorService) {
        super(destination);
        ObjectHelper.notNull(executorService, "executorService");
        this.executorService = executorService;
    }

    public WireTapProcessor(Endpoint destination, ExchangePattern pattern, ExecutorService executorService) {
        super(destination, pattern);
        ObjectHelper.notNull(executorService, "executorService");
        this.executorService = executorService;
    }

    @Override
    public String toString() {
        return "WireTap[" + destination.getEndpointUri() + "]";
    }

    @Override
    public String getTraceLabel() {
        return "wireTap(" + destination.getEndpointUri() + ")";
    }

    public void process(Exchange exchange) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("WireTapProcessor has not been started: " + this);
        }

        // must configure the wire tap beforehand
        final Exchange wireTapExchange = configureExchange(exchange, pattern);

        // send the exchange to the destination using an executor service
        executorService.submit(new Callable<Exchange>() {
            public Exchange call() throws Exception {
                return producerCache.doInProducer(destination, wireTapExchange, pattern, new ProducerCallback<Exchange>() {
                    public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                        log.debug(">>>> (wiretap) {} {}", destination, exchange);
                        producer.process(exchange);
                        return exchange;
                    }
                });
            };
        });
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("WireTapProcessor has not been started: " + this);
        }

        // must configure the wire tap beforehand
        final Exchange wireTapExchange = configureExchange(exchange, pattern);

        // send the exchange to the destination using an executor service
        executorService.submit(new Callable<Exchange>() {
            public Exchange call() throws Exception {
                return producerCache.doInProducer(destination, wireTapExchange, pattern, new ProducerCallback<Exchange>() {
                    public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                        log.debug(">>>> (wiretap) {} {}", destination, exchange);
                        producer.process(exchange);
                        return exchange;
                    }
                });
            };
        });

        // continue routing this synchronously
        callback.done(true);
        return true;
    }


    @Override
    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) {
        Exchange answer;
        if (copy) {
            // use a copy of the original exchange
            answer = configureCopyExchange(exchange);
        } else {
            // use a new exchange
            answer = configureNewExchange(exchange);
        }

        // set property which endpoint we send to
        answer.setProperty(Exchange.TO_ENDPOINT, destination.getEndpointUri());

        // prepare the exchange
        if (newExchangeProcessor != null) {
            try {
                newExchangeProcessor.process(answer);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        } else if (newExchangeExpression != null) {
            Object body = newExchangeExpression.evaluate(answer, Object.class);
            if (body != null) {
                answer.getIn().setBody(body);
            }
        }

        return answer;
    }

    private Exchange configureCopyExchange(Exchange exchange) {
        // must use a copy as we dont want it to cause side effects of the original exchange
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
        // set MEP to InOnly as this wire tap is a fire and forget
        copy.setPattern(ExchangePattern.InOnly);
        return copy;
    }

    private Exchange configureNewExchange(Exchange exchange) {
        return new DefaultExchange(exchange.getFromEndpoint(), ExchangePattern.InOnly);
    }

    public Processor getNewExchangeProcessor() {
        return newExchangeProcessor;
    }

    public void setNewExchangeProcessor(Processor newExchangeProcessor) {
        this.newExchangeProcessor = newExchangeProcessor;
    }

    public Expression getNewExchangeExpression() {
        return newExchangeExpression;
    }

    public void setNewExchangeExpression(Expression newExchangeExpression) {
        this.newExchangeExpression = newExchangeExpression;
    }

    public boolean isCopy() {
        return copy;
    }

    public void setCopy(boolean copy) {
        this.copy = copy;
    }
}
