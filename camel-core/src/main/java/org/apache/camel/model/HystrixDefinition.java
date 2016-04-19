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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "hystrix")
@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixDefinition extends OutputDefinition<HystrixDefinition> {

    @XmlElement
    private HystrixConfigurationDefinition hystrixConfiguration;
    @XmlElementRef
    protected List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
    @XmlElement
    private FallbackDefinition fallback;

    public HystrixDefinition() {
    }

    @Override
    public String toString() {
        return "Hystrix[" + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return "hystrix";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new IllegalStateException("Cannot find camel-hystrix on the classpath.");
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
        if (outputs != null) {
            for (ProcessorDefinition<?> output : outputs) {
                configureChild(output);
            }
        }
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        if (output instanceof FallbackDefinition) {
            fallback = (FallbackDefinition) output;
        } else {
            if (fallback != null) {
                fallback.addOutput(output);
            } else {
                super.addOutput(output);
            }
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

    protected void preCreateProcessor() {
        // move the fallback from outputs to fallback which we need to ensure
        // such as when using the XML DSL
        Iterator<ProcessorDefinition<?>> it = outputs.iterator();
        while (it.hasNext()) {
            ProcessorDefinition<?> out = it.next();
            if (out instanceof FallbackDefinition) {
                fallback = (FallbackDefinition) out;
                it.remove();
            }
        }
    }

    // Getter/Setter
    // -------------------------------------------------------------------------

    public FallbackDefinition getFallback() {
        return fallback;
    }

    public void setFallback(FallbackDefinition fallback) {
        this.fallback = fallback;
    }

    public HystrixConfigurationDefinition getHystrixConfiguration() {
        return hystrixConfiguration;
    }

    public void setHystrixConfiguration(HystrixConfigurationDefinition hystrixConfiguration) {
        this.hystrixConfiguration = hystrixConfiguration;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the fallback node
     */
    public HystrixDefinition fallback() {
        fallback = new FallbackDefinition();
        fallback.setParent(this);
        return this;
    }

    /**
     * Configures the Hystrix EIP
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Hystrix EIP.
     */
    public HystrixConfigurationDefinition configure() {
        hystrixConfiguration = new HystrixConfigurationDefinition(this);
        return hystrixConfiguration;
    }

}
