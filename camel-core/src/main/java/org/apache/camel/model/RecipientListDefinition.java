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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;recipientList/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "recipientList")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecipientListDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<RecipientListDefinition> {

    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String delimiter;
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Boolean stopOnException;

    public RecipientListDefinition() {
    }

    public RecipientListDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    public RecipientListDefinition(Expression expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "RecipientList[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "recipientList";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expression = getExpression().createExpression(routeContext);

        RecipientList answer;
        if (delimiter != null) {
            answer = new RecipientList(expression, delimiter);
        } else {
            answer = new RecipientList(expression);
        }

        if (parallelProcessing != null) {
            answer.setParallelProcessing(isParallelProcessing());
        }
        if (stopOnException != null) {
            answer.setStopOnException(isStopOnException());
        }
        
        answer.setAggregationStrategy(createAggregationStrategy(routeContext));
        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, this);
        if (executorService == null) {
            // fallback to create a new executor
            executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newCachedThreadPool("RecipientList");
        }
        answer.setExecutorService(executorService);

        return answer;
    }
    
    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        if (aggregationStrategy == null && strategyRef != null) {
            aggregationStrategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (aggregationStrategy == null) {
            // fallback to use latest
            aggregationStrategy = new UseLatestAggregationStrategy();
        }
        return aggregationStrategy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProcessorDefinition> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public void addOutput(ProcessorDefinition processorType) {
        // add it to the parent as a recipient list does not support outputs
        getParent().addOutput(processorType);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Set the aggregationStrategy
     *
     * @return the builder
     */
    public RecipientListDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Set the aggregationStrategy
     *
     * @param aggregationStrategyRef a reference to a strategy to lookup
     * @return the builder
     */
    public RecipientListDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Doing the recipient list work in parallel
     *
     * @return the builder
     */
    public RecipientListDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }

    /**
     * Will now stop further processing if an exception occurred during processing of an
     * {@link org.apache.camel.Exchange} and the caused exception will be thrown.
     * <p/>
     * The default behavior is to <b>not</b> stop but continue processing till the end
     *
     * @return the builder
     */
    public RecipientListDefinition stopOnException() {
        setStopOnException(true);
        return this;
    }

    @SuppressWarnings("unchecked")
    public RecipientListDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    @SuppressWarnings("unchecked")
    public RecipientListDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(Boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Boolean isStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(Boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
}
