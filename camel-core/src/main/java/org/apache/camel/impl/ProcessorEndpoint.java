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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * An endpoint which allows exchanges to be sent into it which just invokes a
 * given {@link Processor}. This component does not support the use of
 * consumers.
 * <p/>
 * <br/>Implementors beware that this endpoint creates producers and consumers which
 * doesn't allow you full control of its lifecycle as a {@link org.apache.camel.Service}
 * or {@link org.apache.camel.SuspendableService} would do.
 * If your producers/consumers need more control of its lifecycle it is advised instead to extend
 * {@link org.apache.camel.impl.DefaultEndpoint}, {@link org.apache.camel.impl.DefaultProducer}
 * and {@link org.apache.camel.impl.DefaultConsumer}.
 *
 * @version 
 */
public class ProcessorEndpoint extends DefaultPollingEndpoint {
    private Processor processor;

    protected ProcessorEndpoint() {
    }

    @SuppressWarnings("deprecation")
    public ProcessorEndpoint(String endpointUri, CamelContext context, Processor processor) {
        super(endpointUri);
        this.setCamelContext(context);
        this.processor = processor;
    }

    public ProcessorEndpoint(String endpointUri, Component component, Processor processor) {
        super(endpointUri, component);
        this.processor = processor;
    }

    protected ProcessorEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Deprecated
    public ProcessorEndpoint(String endpointUri, Processor processor) {
        super(endpointUri);
        this.processor = processor;
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                onExchange(exchange);
            }
        };
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        PollingConsumer answer = new ProcessorPollingConsumer(this, getProcessor());
        configurePollingConsumer(answer);
        return answer;
    }

    public Processor getProcessor() throws Exception {
        if (processor == null) {
            processor = createProcessor();
        }
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    protected Processor createProcessor() throws Exception {
        return new Processor() {
            public void process(Exchange exchange) throws Exception {
                onExchange(exchange);
            }
        };
    }

    protected void onExchange(Exchange exchange) throws Exception {
        getProcessor().process(exchange);
    }

    public boolean isSingleton() {
        return true;
    }
}
