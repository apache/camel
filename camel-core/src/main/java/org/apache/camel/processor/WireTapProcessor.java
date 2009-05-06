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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Processor for wire tapping exchanges to an endpoint destination.
 *
 * @version $Revision$
 */
public class WireTapProcessor extends SendProcessor {

    private ExecutorService executorService;

    // expression or processor used for populating a new exchange to send
    // as opposed to traditional wiretap that sends a copy of the original exchange
    private Expression newExchangeExpression;
    private Processor newExchangeProcessor;

    public WireTapProcessor(Endpoint destination) {
        super(destination);
    }

    public WireTapProcessor(Endpoint destination, ExchangePattern pattern) {
        super(destination, pattern);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
        }
        super.doStop();
    }

    @Override
    public String toString() {
        return "wireTap(" + destination.getEndpointUri() + ")";
    }

    public void process(Exchange exchange) throws Exception {
        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
            } else {
                throw new IllegalStateException("No producer, this processor has not been started!");
            }
        } else {
            final Exchange wireTapExchange = configureExchange(exchange);

            // use submit instead of execute to force it to use a new thread, execute might
            // decide to use current thread, so we must submit a new task
            // as we dont care for the response we dont hold the future object and wait for the result
            getExecutorService().submit(new Callable<Object>() {
                public Object call() throws Exception {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Processing wiretap: " + wireTapExchange);
                    }
                    producer.process(wireTapExchange);
                    return null;
                }
            });
        }
    }

    @Override
    protected Exchange configureExchange(Exchange exchange) {
        if (newExchangeProcessor == null && newExchangeExpression == null) {
            // use a copy of the original exchange
            return configureCopyExchange(exchange);
        } else {
            // use a new exchange
            return configureNewExchange(exchange);
        }
    }

    private Exchange configureCopyExchange(Exchange exchange) {
        // must use a copy as we dont want it to cause side effects of the original exchange
        Exchange copy = exchange.newCopy();
        // set MEP to InOnly as this wire tap is a fire and forget
        copy.setPattern(ExchangePattern.InOnly);
        return copy;
    }

    private Exchange configureNewExchange(Exchange exchange) {
        Exchange answer = new DefaultExchange(exchange.getContext(), ExchangePattern.InOnly);
        // use destination os origin of this new exchange
        answer.setFromEndpoint(getDestination());

        // prepare the exchange
        if (newExchangeProcessor != null) {
            try {
                newExchangeProcessor.process(answer);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        } else {
            Object body = newExchangeExpression.evaluate(answer, Object.class);
            if (body != null) {
                answer.getIn().setBody(body);
            }
        }

        return answer;
    }

    public ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    private ExecutorService createExecutorService() {
        return ExecutorServiceHelper.newScheduledThreadPool(5, this.toString(), true);
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
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
}
