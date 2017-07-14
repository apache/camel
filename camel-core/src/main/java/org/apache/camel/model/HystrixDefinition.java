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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Hystrix Circuit Breaker EIP
 */
@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "hystrix")
@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixDefinition extends ProcessorDefinition<HystrixDefinition> {

    @XmlElement
    private HystrixConfigurationDefinition hystrixConfiguration;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
    @XmlTransient
    private OnFallbackDefinition onFallback;
    @XmlAttribute
    private String hystrixConfigurationRef;

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
        if (output instanceof OnFallbackDefinition) {
            onFallback = (OnFallbackDefinition) output;
        } else {
            if (onFallback != null) {
                onFallback.addOutput(output);
            } else {
                super.addOutput(output);
            }
        }
    }

    @Override
    public ProcessorDefinition<?> end() {
        if (onFallback != null) {
            // end fallback as well
            onFallback.end();
        }
        return super.end();
    }

    protected void preCreateProcessor() {
        // move the fallback from outputs to fallback which we need to ensure
        // such as when using the XML DSL
        Iterator<ProcessorDefinition<?>> it = outputs.iterator();
        while (it.hasNext()) {
            ProcessorDefinition<?> out = it.next();
            if (out instanceof OnFallbackDefinition) {
                onFallback = (OnFallbackDefinition) out;
                it.remove();
            }
        }
    }

    // Getter/Setter
    // -------------------------------------------------------------------------

    public HystrixConfigurationDefinition getHystrixConfiguration() {
        return hystrixConfiguration;
    }

    public void setHystrixConfiguration(HystrixConfigurationDefinition hystrixConfiguration) {
        this.hystrixConfiguration = hystrixConfiguration;
    }

    public String getHystrixConfigurationRef() {
        return hystrixConfigurationRef;
    }

    /**
     * Refers to a Hystrix configuration to use for configuring the Hystrix EIP.
     */
    public void setHystrixConfigurationRef(String hystrixConfigurationRef) {
        this.hystrixConfigurationRef = hystrixConfigurationRef;
    }

    public OnFallbackDefinition getOnFallback() {
        return onFallback;
    }

    public void setOnFallback(OnFallbackDefinition onFallback) {
        this.onFallback = onFallback;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the group key to use. The default value is CamelHystrix.
     */
    public HystrixDefinition groupKey(String groupKey) {
        hystrixConfiguration().groupKey(groupKey);
        return this;
    }

    /**
     * Sets the thread pool key to use. The default value is CamelHystrix.
     */
    public HystrixDefinition threadPoolKey(String threadPoolKey) {
        hystrixConfiguration().threadPoolKey(threadPoolKey);
        return this;
    }

    /**
     * Configures the Hystrix EIP
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Hystrix EIP.
     */
    public HystrixConfigurationDefinition hystrixConfiguration() {
        hystrixConfiguration = hystrixConfiguration == null ? new HystrixConfigurationDefinition(this) : hystrixConfiguration;
        return hystrixConfiguration;
    }
    
    /**
     * Configures the Hystrix EIP using the given configuration
     */
    public HystrixDefinition hystrixConfiguration(HystrixConfigurationDefinition configuration) {
        hystrixConfiguration = configuration;
        return this;
    }

    /**
     * Refers to a Hystrix configuration to use for configuring the Hystrix EIP.
     */
    public HystrixDefinition hystrixConfiguration(String ref) {
        hystrixConfigurationRef = ref;
        return this;
    }

    /**
     * The Hystrix fallback route path to execute that does <b>not</b> go over the network.
     * <p>
     * This should be a static or cached result that can immediately be returned upon failure.
     * If the fallback requires network connection then use {@link #onFallbackViaNetwork()}.
     */
    public HystrixDefinition onFallback() {
        onFallback = new OnFallbackDefinition();
        onFallback.setParent(this);
        return this;
    }

    /**
     * The Hystrix fallback route path to execute that will go over the network.
     * <p/>
     * If the fallback will go over the network it is another possible point of failure and so it also needs to be
     * wrapped by a HystrixCommand. It is important to execute the fallback command on a separate thread-pool,
     * otherwise if the main command were to become latent and fill the thread-pool
     * this would prevent the fallback from running if the two commands share the same pool.
     */
    public HystrixDefinition onFallbackViaNetwork() {
        onFallback = new OnFallbackDefinition();
        onFallback.setFallbackViaNetwork(true);
        onFallback.setParent(this);
        return this;
    }

}
