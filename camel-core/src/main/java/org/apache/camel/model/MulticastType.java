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
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.processor.interceptor.StreamCachingInterceptor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;multicast/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "multicast")
@XmlAccessorType(XmlAccessType.FIELD)
public class MulticastType extends OutputType<ProcessorType> {
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public String toString() {
        return "Multicast[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "multicast";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createOutputsProcessor(routeContext);
    }

    protected Processor createCompositeProcessor(List<Processor> list) {
        if (aggregationStrategy == null) {
            aggregationStrategy = new UseLatestAggregationStrategy();
        }
        return new MulticastProcessor(list, aggregationStrategy, isParallelProcessing(), threadPoolExecutor);
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public MulticastType setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
        return this;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing != null ? parallelProcessing : false;
    }

    public MulticastType setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
        return this;
    }

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    public MulticastType setThreadPoolExecutor(ThreadPoolExecutor executor) {
        this.threadPoolExecutor = executor;
        return this;
    }

    @Override
    protected Processor wrapProcessorInInterceptors(RouteContext routeContext, Processor target) throws Exception {
        // No need to wrap me in interceptors as they are all applied directly to my children
        return new StreamCachingInterceptor(target);
    }
}