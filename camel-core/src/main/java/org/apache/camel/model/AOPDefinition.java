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
import java.util.Collection;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.processor.AOPProcessor;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Does processing before and/or after the route is completed
 *
 * @deprecated will be removed in the future. You can for example use {@link Processor} and
 * {@link org.apache.camel.spi.InterceptStrategy} to do AOP in Camel.
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "aop")
@XmlAccessorType(XmlAccessType.FIELD)
@Deprecated
public class AOPDefinition extends OutputDefinition<AOPDefinition> {
    @XmlAttribute
    private String beforeUri;
    @XmlAttribute
    private String afterUri;
    @XmlAttribute
    private String afterFinallyUri;

    public AOPDefinition() {
    }

    @Override
    public String toString() {
        return "AOP[" + getOutputs() + "]";
    }

    public String getBeforeUri() {
        return beforeUri;
    }

    /**
     * Endpoint to call in AOP before.
     */
    public void setBeforeUri(String beforeUri) {
        this.beforeUri = beforeUri;
    }

    public String getAfterUri() {
        return afterUri;
    }

    /**
     * Endpoint to call in AOP after.
     * <p/>
     * The difference between after and afterFinally is that afterFinally is invoked from a finally block
     * so it will always be invoked no matter what, eg also in case of an exception occur.
     */
    public void setAfterUri(String afterUri) {
        this.afterUri = afterUri;
    }

    public String getAfterFinallyUri() {
        return afterFinallyUri;
    }

    /**
     * Endpoint to call in AOP after finally.
     * <p/>
     * The difference between after and afterFinally is that afterFinally is invoked from a finally block
     * so it will always be invoked no matter what, eg also in case of an exception occur.
     */
    public void setAfterFinallyUri(String afterFinallyUri) {
        this.afterFinallyUri = afterFinallyUri;
    }

    @Override
    public String getLabel() {
        return "aop";
    }

    @Override
    public Processor createProcessor(final RouteContext routeContext) throws Exception {
        // either before or after must be provided
        if (beforeUri == null && afterUri == null && afterFinallyUri == null) {
            throw new IllegalArgumentException("At least one of before, after or afterFinally must be provided on: " + this);
        }

        // use a pipeline to assemble the before and target processor
        // and the after if not afterFinally
        Collection<ProcessorDefinition<?>> pipe = new ArrayList<ProcessorDefinition<?>>();

        Processor finallyProcessor = null;

        if (beforeUri != null) {
            pipe.add(new ToDefinition(beforeUri));
        }
        pipe.addAll(getOutputs());

        if (afterUri != null) {
            pipe.add(new ToDefinition(afterUri));
        } else if (afterFinallyUri != null) {
            finallyProcessor = createProcessor(routeContext, new ToDefinition(afterFinallyUri));
        }

        Processor tryProcessor = createOutputsProcessor(routeContext, pipe);

        // the AOP processor is based on TryProcessor so we do not have any catches
        return new AOPProcessor(tryProcessor, null, finallyProcessor);
    }

    /**
     * Uses a AOP around.
     *
     * @param beforeUri the uri of the before endpoint
     * @param afterUri  the uri of the after endpoint
     * @return the builder
     */
    public AOPDefinition around(@AsEndpointUri String beforeUri, @AsEndpointUri String afterUri) {
        this.beforeUri = beforeUri;
        this.afterUri = afterUri;
        this.afterFinallyUri = null;
        return this;
    }

    /**
     * Uses a AOP around with after being invoked in a finally block
     *
     * @param beforeUri the uri of the before endpoint
     * @param afterUri  the uri of the after endpoint
     * @return the builder
     */
    public AOPDefinition aroundFinally(@AsEndpointUri String beforeUri, @AsEndpointUri String afterUri) {
        this.beforeUri = beforeUri;
        this.afterUri = null;
        this.afterFinallyUri = afterUri;
        return this;
    }

    /**
     * Uses a AOP before.
     *
     * @param beforeUri the uri of the before endpoint
     * @return the builder
     */
    public AOPDefinition before(@AsEndpointUri String beforeUri) {
        this.beforeUri = beforeUri;
        this.afterUri = null;
        this.afterFinallyUri = null;
        return this;
    }

    /**
     * Uses a AOP after.
     *
     * @param afterUri  the uri of the after endpoint
     * @return the builder
     */
    public AOPDefinition after(@AsEndpointUri String afterUri) {
        this.beforeUri = null;
        this.afterUri = afterUri;
        this.afterFinallyUri = null;
        return this;
    }

    /**
     * Uses a AOP after with after being invoked in a finally block.
     *
     * @param afterUri  the uri of the after endpoint
     * @return the builder
     */
    public AOPDefinition afterFinally(@AsEndpointUri String afterUri) {
        this.beforeUri = null;
        this.afterUri = null;
        this.afterFinallyUri = afterUri;
        return this;
    }
}
