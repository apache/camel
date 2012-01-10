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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RouteNode;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Traceable;

/**
 * A default implementation of the {@link org.apache.camel.RouteNode}
 *
 * @version 
 */
public class DefaultRouteNode implements RouteNode {

    private Expression expression;
    private Processor processor;
    private ProcessorDefinition<?> processorDefinition;

    public DefaultRouteNode(ProcessorDefinition<?> processorDefinition, Processor processor) {
        this.processor = processor;
        this.processorDefinition = processorDefinition;
    }

    public DefaultRouteNode(ProcessorDefinition<?> processorDefinition, Expression expression) {
        this.processorDefinition = processorDefinition;
        this.expression = expression;
    }

    public Processor getProcessor() {
        return processor;
    }

    public ProcessorDefinition<?> getProcessorDefinition() {
        return processorDefinition;
    }

    public String getLabel(Exchange exchange) {
        if (expression != null) {
            return expression.evaluate(exchange, String.class);
        }

        Processor target = processor;
        if (target != null) {
            // can be wrapped
            if (target instanceof InstrumentationProcessor) {
                target = ((InstrumentationProcessor) target).getProcessor();
            }

            if (target instanceof Traceable) {
                Traceable trace = (Traceable) target;
                return trace.getTraceLabel();
            }
        }

        // default then to definition
        return processorDefinition.getLabel();
    }

    public boolean isAbstract() {
        return processor == null;
    }

    @Override
    public String toString() {
        return "RouteNode[" + processorDefinition + "]";
    }
}
