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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;splitter/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "splitter")
@XmlAccessorType(XmlAccessType.FIELD)
public class SplitterType extends ExpressionNode {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlTransient
    private ThreadPoolExecutor threadPoolExecutor;
    
    public SplitterType() {
    }

    public SplitterType(Expression expression) {
        super(expression);
    }

    public SplitterType(ExpressionType expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "Splitter[ " + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "splitter";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        if (aggregationStrategy == null) {
            aggregationStrategy = new UseLatestAggregationStrategy();
        }
        if (threadPoolExecutor == null) {
            threadPoolExecutor = new ThreadPoolExecutor(4, 16, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue());
        }
        return new Splitter(getExpression().createExpression(routeContext), childProcessor, aggregationStrategy,
                isParallelProcessing(), threadPoolExecutor);
    }
    
    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
    
    public boolean isParallelProcessing() {
        return parallelProcessing != null ? parallelProcessing : false;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
    }
}
