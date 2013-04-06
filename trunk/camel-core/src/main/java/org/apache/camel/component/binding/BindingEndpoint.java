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
package org.apache.camel.component.binding;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Binding;
import org.apache.camel.spi.HasBinding;
import org.apache.camel.util.ExchangeHelper;

/**
 * Applies a {@link org.apache.camel.spi.Binding} to an underlying {@link Endpoint} so that the binding processes messages
 * before its sent to the endpoint and processes messages received by the endpoint consumer before its passed
 * to the real consumer.
 */
public class BindingEndpoint extends DefaultEndpoint implements HasBinding {
    private final Binding binding;
    private final Endpoint delegate;

    public BindingEndpoint(String uri, Component component, Binding binding, Endpoint delegate) {
        super(uri, component);
        this.binding = binding;
        this.delegate = delegate;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BindingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Processor bindingProcessor = new BindingConsumerProcessor(this, processor);
        return delegate.createConsumer(bindingProcessor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Binding getBinding() {
        return binding;
    }

    public Endpoint getDelegate() {
        return delegate;
    }


    /**
     * Applies the {@link Binding} processor to the given exchange before passing it on to the delegateProcessor (either a producer or consumer)
     */
    public void pipelineBindingProcessor(Processor bindingProcessor, Exchange exchange, Processor delegateProcessor) throws Exception {
        // use same exchange - seems Pipeline does these days
        Exchange bindingExchange = exchange;
        bindingProcessor.process(bindingExchange);
        Exchange delegateExchange = createNextExchange(bindingExchange);
        ExchangeHelper.copyResults(bindingExchange, delegateExchange);
        delegateProcessor.process(delegateExchange);
    }

    // TODO this code was copied from Pipeline - should make it static and reuse the code?
    protected Exchange createNextExchange(Exchange previousExchange) {
        Exchange answer = previousExchange;

        // now lets set the input of the next exchange to the output of the
        // previous message if it is not null
        if (answer.hasOut()) {
            answer.setIn(answer.getOut());
            answer.setOut(null);
        }
        return answer;
    }

}
