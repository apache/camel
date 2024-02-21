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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Route to be executed when Circuit Breaker EIP executes fallback
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "onFallback")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnFallbackDefinition extends ProcessorDefinition<OnFallbackDefinition> implements OutputNode {

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "false", javaType = "java.lang.Boolean")
    private String fallbackViaNetwork;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();

    public OnFallbackDefinition() {
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public String toString() {
        if (Boolean.toString(true).equals(fallbackViaNetwork)) {
            return "OnFallbackViaNetwork[" + getOutputs() + "]";
        } else if (fallbackViaNetwork == null || Boolean.toString(false).equals(fallbackViaNetwork)) {
            return "OnFallback[" + getOutputs() + "]";
        } else {
            return "OnFallback[viaNetwork=" + fallbackViaNetwork + "," + getOutputs() + "]";
        }
    }

    @Override
    public String getShortName() {
        return "onFallback";
    }

    @Override
    public String getLabel() {
        String name;
        if (Boolean.toString(true).equals(fallbackViaNetwork)) {
            name = "OnFallbackViaNetwork";
        } else if (fallbackViaNetwork == null || Boolean.toString(false).equals(fallbackViaNetwork)) {
            name = "onFallback";
        } else {
            name = "onFallback(viaNetwork=" + fallbackViaNetwork + ")";
        }
        return getOutputs().stream().map(ProcessorDefinition::getLabel)
                .collect(Collectors.joining(",", name + "[", "]"));
    }

    public String getFallbackViaNetwork() {
        return fallbackViaNetwork;
    }

    /**
     * Whether the fallback goes over the network.
     * <p/>
     * If the fallback will go over the network it is another possible point of failure. It is important to execute the
     * fallback command on a separate thread-pool, otherwise if the main command were to become latent and fill the
     * thread-pool this would prevent the fallback from running if the two commands share the same pool.
     */
    public void setFallbackViaNetwork(String fallbackViaNetwork) {
        this.fallbackViaNetwork = fallbackViaNetwork;
    }

}
