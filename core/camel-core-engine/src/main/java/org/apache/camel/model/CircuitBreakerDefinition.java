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
package org.apache.camel.model;

import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "circuitBreaker")
@XmlAccessorType(XmlAccessType.FIELD)
public class CircuitBreakerDefinition extends OutputDefinition<CircuitBreakerDefinition> {

    @XmlElement
    private HystrixConfigurationDefinition hystrixConfiguration;
    @XmlElement
    private Resilience4jConfigurationDefinition resilience4jConfiguration;
    @XmlAttribute
    private String configurationRef;
    @XmlTransient
    private OnFallbackDefinition onFallback;

    public CircuitBreakerDefinition() {
    }

    @Override
    public String toString() {
        return "CircuitBreaker[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "circuitBreaker";
    }

    @Override
    public String getLabel() {
        return "circuitBreaker";
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        if (output instanceof OnFallbackDefinition) {
            onFallback = (OnFallbackDefinition)output;
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

    @Override
    public void preCreateProcessor() {
        // move the fallback from outputs to fallback which we need to ensure
        // such as when using the XML DSL
        Iterator<ProcessorDefinition<?>> it = outputs.iterator();
        while (it.hasNext()) {
            ProcessorDefinition<?> out = it.next();
            if (out instanceof OnFallbackDefinition) {
                onFallback = (OnFallbackDefinition)out;
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

    public Resilience4jConfigurationCommon getResilience4jConfiguration() {
        return resilience4jConfiguration;
    }

    public void setResilience4jConfiguration(Resilience4jConfigurationDefinition resilience4jConfiguration) {
        this.resilience4jConfiguration = resilience4jConfiguration;
    }

    public String getConfigurationRef() {
        return configurationRef;
    }

    /**
     * Refers to a circuit breaker configuration (such as hystrix, resillience4j, or microprofile-fault-tolerance)
     * to use for configuring the circuit breaker EIP.
     */
    public void setConfigurationRef(String configurationRef) {
        this.configurationRef = configurationRef;
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
     * Configures the circuit breaker to use Hystrix.
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the
     * Circuit Breaker EIP.
     */
    public HystrixConfigurationDefinition hystrixConfiguration() {
        hystrixConfiguration = hystrixConfiguration == null ? new HystrixConfigurationDefinition(this) : hystrixConfiguration;
        return hystrixConfiguration;
    }

    /**
     * Configures the circuit breaker to use Hystrix with the given configuration.
     */
    public CircuitBreakerDefinition hystrixConfiguration(HystrixConfigurationDefinition configuration) {
        hystrixConfiguration = configuration;
        return this;
    }

    /**
     * Configures the circuit breaker to use Resilience4j.
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the
     * Circuit Breaker EIP.
     */
    public Resilience4jConfigurationDefinition resilience4jConfiguration() {
        resilience4jConfiguration = resilience4jConfiguration == null ? new Resilience4jConfigurationDefinition(this) : resilience4jConfiguration;
        return resilience4jConfiguration;
    }

    /**
     * Configures the circuit breaker to use Resilience4j with the given configuration.
     */
    public CircuitBreakerDefinition resilience4jConfiguration(Resilience4jConfigurationDefinition configuration) {
        resilience4jConfiguration = configuration;
        return this;
    }

    /**
     * Refers to a configuration to use for configuring the circuit breaker.
     */
    public CircuitBreakerDefinition configuration(String ref) {
        configurationRef = ref;
        return this;
    }

    /**
     * The fallback route path to execute that does <b>not</b> go over
     * the network.
     * <p>
     * This should be a static or cached result that can immediately be returned
     * upon failure. If the fallback requires network connection then use
     * {@link #onFallbackViaNetwork()}.
     */
    public CircuitBreakerDefinition onFallback() {
        onFallback = new OnFallbackDefinition();
        onFallback.setParent(this);
        return this;
    }

    /**
     * The fallback route path to execute that will go over the network.
     * <p/>
     * If the fallback will go over the network it is another possible point of failure.
     */
    public CircuitBreakerDefinition onFallbackViaNetwork() {
        onFallback = new OnFallbackDefinition();
        onFallback.setFallbackViaNetwork(Boolean.toString(true));
        onFallback.setParent(this);
        return this;
    }

}
