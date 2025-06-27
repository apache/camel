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
package org.apache.camel.telemetry;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class TraceProcessorsInterceptStrategy implements InterceptStrategy {

    private Tracer tracer;

    public TraceProcessorsInterceptStrategy(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext,
            NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {
        //return new TraceProcessor(target, processorDefinition);
        return new DelegateAsyncProcessor(new TraceProcessor(target, processorDefinition));
    }

    private class TraceProcessor implements Processor {
        private final NamedNode processorDefinition;
        private final Processor target;

        public TraceProcessor(Processor target, NamedNode processorDefinition) {
            this.target = target;
            this.processorDefinition = processorDefinition;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            String processor = processorDefinition.getId() + "-" + processorDefinition.getShortName();
            if (!tracer.exclude(processor, exchange.getContext())) {
                tracer.beginProcessorSpan(exchange, processor);
                try {
                    target.process(exchange);
                } finally {
                    tracer.endProcessorSpan(exchange, processor);
                }
            }
        }
    }

}
