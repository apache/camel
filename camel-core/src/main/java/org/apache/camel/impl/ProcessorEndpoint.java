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
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;

/**
 * An endpoint which allows exchanges to be sent into it which just invokes a
 * given {@link Processor}. This component does not support the use of
 * consumers.
 * 
 * @version $Revision: 1.1 $
 */
public class ProcessorEndpoint extends DefaultPollingEndpoint<Exchange> {
    private final Processor processor;

    public ProcessorEndpoint(String endpointUri, CamelContext context, Processor processor) {
        super(endpointUri, context);
        this.processor = processor;
    }

    public ProcessorEndpoint(String endpointUri, Component component, Processor processor) {
        super(endpointUri, component);
        this.processor = processor;
    }


    public Producer<Exchange> createProducer() throws Exception {
        return new DefaultProducer<Exchange>(this) {
            public void process(Exchange exchange) throws Exception {
                onExchange(exchange);
            }
        };
    }

    @Override
    public PollingConsumer<Exchange> createPollingConsumer() throws Exception {
        return new ProcessorPollingConsumer(this, getProcessor());
    }

    public Processor getProcessor() {
        if (processor == null) {
            return new Processor() {
                public void process(Exchange exchange) throws Exception {
                    onExchange(exchange);
                }
            };
        }
        return processor;
    }

    protected void onExchange(Exchange exchange) throws Exception {
        processor.process(exchange);
    }

    public boolean isSingleton() {
        return true;
    }
}
