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

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * Route to be executed when Hystrix EIP executes fallback
 */
@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "onFallback")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnFallbackDefinition extends OutputDefinition<OnFallbackDefinition> {

    @XmlAttribute
    @Metadata(label = "command", defaultValue = "false")
    private Boolean fallbackViaNetwork;

    public OnFallbackDefinition() {
    }

    @Override
    public String toString() {
        if (fallbackViaNetwork != null && fallbackViaNetwork) {
            return "OnFallbackViaNetwork[" + getOutputs() + "]";
        } else {
            return "OnFallback[" + getOutputs() + "]";
        }
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return this.createChildProcessor(routeContext, false);
    }

    @Override
    public String getLabel() {
        String name = fallbackViaNetwork != null && fallbackViaNetwork ? "onFallbackViaNetwork" : "onFallback";
        CollectionStringBuffer buffer = new CollectionStringBuffer(name);
        buffer.append("[");
        List<ProcessorDefinition<?>> list = getOutputs();
        for (ProcessorDefinition<?> type : list) {
            buffer.append(type.getLabel());
        }
        buffer.append("]");
        return buffer.toString();
    }

    public Boolean getFallbackViaNetwork() {
        return fallbackViaNetwork;
    }

    /**
     * Whether the fallback goes over the network.
     * <p/>
     * If the fallback will go over the network it is another possible point of failure and so it also needs to be
     * wrapped by a HystrixCommand. It is important to execute the fallback command on a separate thread-pool,
     * otherwise if the main command were to become latent and fill the thread-pool
     * this would prevent the fallback from running if the two commands share the same pool.
     */
    public void setFallbackViaNetwork(Boolean fallbackViaNetwork) {
        this.fallbackViaNetwork = fallbackViaNetwork;
    }

    public boolean isFallbackViaNetwork() {
        // is default false
        return fallbackViaNetwork != null && fallbackViaNetwork;
    }

}
