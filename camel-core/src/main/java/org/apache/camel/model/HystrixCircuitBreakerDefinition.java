/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.processor.HystrixCircuitBreakerProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "hystrixCircuitBreaker")
@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixCircuitBreakerDefinition extends OutputDefinition<HystrixCircuitBreakerDefinition> {

    // TODO: we can rename to circuitBreaker and then deprecated the CB in the load balancer
    // the trick is to avoid a clash in the generated xml schema
    // so for know we call it hystrixCircuitBreaker

    @XmlElement
    private FallbackDefinition fallback;

    public HystrixCircuitBreakerDefinition() {
    }

    @Override
    public String toString() {
        return "HystrixCircuitBreaker[" + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return "hystrixCircuitBreaker";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor children = this.createChildProcessor(routeContext, true);

        Processor fallbackProcessor = null;
        if (fallback != null) {
            fallbackProcessor = createProcessor(routeContext, fallback);
        }
        return new HystrixCircuitBreakerProcessor(children, fallbackProcessor);
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        if (fallback != null) {
            fallback.addOutput(output);
        } else {
            super.addOutput(output);
        }
    }

    @Override
    public ProcessorDefinition<?> end() {
        if (fallback != null) {
            // end fallback as well
            fallback.end();
        }
        return super.end();
    }

    public FallbackDefinition getFallback() {
        return fallback;
    }

    public void setFallback(FallbackDefinition fallback) {
        this.fallback = fallback;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the otherwise node
     *
     * @return the builder
     */
    public HystrixCircuitBreakerDefinition fallback() {
        fallback = new FallbackDefinition();
        fallback.setParent(this);
        return this;
    }

}
