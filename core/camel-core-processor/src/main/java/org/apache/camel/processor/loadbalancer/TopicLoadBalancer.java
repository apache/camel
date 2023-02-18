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
package org.apache.camel.processor.loadbalancer;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A {@link LoadBalancer} implementations which sends to all destinations (rather like JMS Topics).
 * <p/>
 * The {@link org.apache.camel.processor.MulticastProcessor} is more powerful as it offers option to run in parallel and
 * decide whether or not to stop on failure etc.
 */
public class TopicLoadBalancer extends LoadBalancerSupport {

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        AsyncProcessor[] processors = doGetProcessors();
        exchange.getContext().getCamelContextExtension().getReactiveExecutor()
                .schedule(new State(exchange, callback, processors)::run);
        return false;
    }

    protected class State {
        final Exchange exchange;
        final AsyncCallback callback;
        final AsyncProcessor[] processors;
        int index;

        public State(Exchange exchange, AsyncCallback callback, AsyncProcessor[] processors) {
            this.exchange = exchange;
            this.callback = callback;
            this.processors = processors;
        }

        public void run() {
            if (index < processors.length) {
                AsyncProcessor processor = processors[index++];
                Exchange copy = copyExchangeStrategy(processor, exchange);
                processor.process(copy, doneSync -> done(copy));
            } else {
                callback.done(false);
            }
        }

        public void done(Exchange current) {
            if (current.getException() != null) {
                exchange.setException(current.getException());
                callback.done(false);
            } else {
                exchange.getContext().getCamelContextExtension().getReactiveExecutor().schedule(this::run);
            }
        }
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint. Derived classes such as the
     * {@link org.apache.camel.processor.Pipeline Pipeline} will not clone the exchange
     *
     * @param  processor the processor that will send the exchange
     * @param  exchange  the exchange
     * @return           the current exchange if no copying is required such as for a pipeline otherwise a new copy of
     *                   the exchange is returned.
     */
    protected Exchange copyExchangeStrategy(Processor processor, Exchange exchange) {
        return exchange.copy();
    }

}
