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
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.processor.SendAsyncProcessor;
import org.apache.camel.processor.UnitOfWorkProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;to/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDefinition extends SendDefinition<ToDefinition> {
    @XmlTransient
    private final List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
    @XmlAttribute(required = false)
    private ExchangePattern pattern;
    @XmlAttribute(required = false)
    private Boolean async = Boolean.FALSE;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Integer poolSize;

    public ToDefinition() {
    }

    public ToDefinition(String uri) {
        setUri(uri);
    }

    public ToDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    public ToDefinition(String uri, ExchangePattern pattern) {
        this(uri);
        this.pattern = pattern;
    }

    public ToDefinition(Endpoint endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern;
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (async == null || !async) {
            // when sync then let super create the processor
            return super.createProcessor(routeContext);
        }

        if (executorServiceRef != null) {
            executorService = routeContext.lookup(executorServiceRef, ExecutorService.class);
        }
        if (executorService == null && poolSize != null) {
            executorService = ExecutorServiceHelper.newScheduledThreadPool(poolSize, "ToAsync[" + getLabel() + "]", true);
        }

        // create the child processor which is the async route
        Processor childProcessor = routeContext.createProcessor(this);

        // wrap it in a unit of work so the route that comes next is also done in a unit of work
        UnitOfWorkProcessor uow = new UnitOfWorkProcessor(childProcessor);

        // create async processor
        Endpoint endpoint = resolveEndpoint(routeContext);

        SendAsyncProcessor async = new SendAsyncProcessor(endpoint, getPattern(), uow);
        if (executorService != null) {
            async.setExecutorService(executorService);
        }
        if (poolSize != null) {
            async.setPoolSize(poolSize);
        }

        return async;
    }

    @Override
    public String toString() {
        if (async != null && async) {
            return "ToAsync[" + getLabel() + "] -> " + getOutputs();
        } else {
            return "To[" + getLabel() + "]";
        }
    }

    @Override
    public String getShortName() {
        return "to";
    }

    @Override
    public ExchangePattern getPattern() {
        return pattern;
    }

    public Boolean isAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    /**
     * Setting the executor service for executing the async routing.
     *
     * @return the builder
     */
    public ToDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Setting the executor service for executing the async routing.
     *
     * @return the builder
     */
    public ToDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Setting the core pool size for the underlying {@link java.util.concurrent.ExecutorService}.
     *
     * @return the builder
     */
    public ToDefinition poolSize(int poolSize) {
        setPoolSize(poolSize);
        return this;
    }
}
