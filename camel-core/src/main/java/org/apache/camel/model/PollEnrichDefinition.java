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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Enriches messages with data polled from a secondary resource
 *
 * @see org.apache.camel.processor.Enricher
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "pollEnrich")
@XmlAccessorType(XmlAccessType.FIELD)
public class PollEnrichDefinition extends NoOutputExpressionNode {
    @XmlAttribute @Metadata(defaultValue = "-1")
    private Long timeout;
    @XmlAttribute(name = "strategyRef")
    private String aggregationStrategyRef;
    @XmlAttribute(name = "strategyMethodName")
    private String aggregationStrategyMethodName;
    @XmlAttribute(name = "strategyMethodAllowNull")
    private Boolean aggregationStrategyMethodAllowNull;
    @XmlAttribute
    private Boolean aggregateOnException;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlAttribute
    private Integer cacheSize;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoint;

    public PollEnrichDefinition() {
    }

    public PollEnrichDefinition(AggregationStrategy aggregationStrategy, long timeout) {
        this.aggregationStrategy = aggregationStrategy;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "PollEnrich[" + getExpression() + "]";
    }
    
    @Override
    public String getLabel() {
        return "pollEnrich[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {

        // if no timeout then we should block, and there use a negative timeout
        long time = timeout != null ? timeout : -1;
        boolean isIgnoreInvalidEndpoint = getIgnoreInvalidEndpoint() != null && getIgnoreInvalidEndpoint();
        Expression exp = getExpression().createExpression(routeContext);

        PollEnricher enricher = new PollEnricher(exp, time);

        AggregationStrategy strategy = createAggregationStrategy(routeContext);
        if (strategy == null) {
            enricher.setDefaultAggregationStrategy();
        } else {
            enricher.setAggregationStrategy(strategy);
        }
        if (getAggregateOnException() != null) {
            enricher.setAggregateOnException(getAggregateOnException());
        }
        if (getCacheSize() != null) {
            enricher.setCacheSize(getCacheSize());
        }
        enricher.setIgnoreInvalidEndpoint(isIgnoreInvalidEndpoint);

        return enricher;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && aggregationStrategyRef != null) {
            Object aggStrategy = routeContext.lookup(aggregationStrategyRef, Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, getAggregationStrategyMethodName());
                if (getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(getAggregationStrategyMethodAllowNull());
                    adapter.setAllowNullOldExchange(getAggregationStrategyMethodAllowNull());
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + aggregationStrategyRef);
            }
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Timeout in millis when polling from the external service.
     * <p/>
     * The timeout has influence about the poll enrich behavior. It basically operations in three different modes:
     * <ul>
     *     <li>negative value - Waits until a message is available and then returns it. Warning that this method could block indefinitely if no messages are available.</li>
     *     <li>0 - Attempts to receive a message exchange immediately without waiting and returning <tt>null</tt> if a message exchange is not available yet.</li>
     *     <li>positive value - Attempts to receive a message exchange, waiting up to the given timeout to expire if a message is not yet available. Returns <tt>null</tt> if timed out</li>
     * </ul>
     * The default value is -1 and therefore the method could block indefinitely, and therefore its recommended to use a timeout value
     */
    public PollEnrichDefinition timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to merge the reply from the external service, into a single outgoing message.
     * By default Camel will use the reply from the external service as outgoing message.
     */
    public PollEnrichDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Refers to an AggregationStrategy to be used to merge the reply from the external service, into a single outgoing message.
     * By default Camel will use the reply from the external service as outgoing message.
     */
    public PollEnrichDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setAggregationStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     */
    public PollEnrichDefinition aggregationStrategyMethodName(String aggregationStrategyMethodName) {
        setAggregationStrategyMethodName(aggregationStrategyMethodName);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich.
     * If this option is true then null values is used as the oldExchange (when no data to enrich),
     * when using POJOs as the AggregationStrategy.
     */
    public PollEnrichDefinition aggregationStrategyMethodAllowNull(boolean aggregationStrategyMethodAllowNull) {
        setAggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull);
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was an exception thrown while trying
     * to retrieve the data to enrich from the resource. Setting this option to true allows end users to control what
     * to do if there was an exception in the aggregate method. For example to suppress the exception
     * or set a custom message body etc.
     */
    public PollEnrichDefinition aggregateOnException(boolean aggregateOnException) {
        setAggregateOnException(aggregateOnException);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ConsumerCache} which is used
     * to cache and reuse consumers when uris are reused.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public PollEnrichDefinition cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public PollEnrichDefinition ignoreInvalidEndpoint() {
        setIgnoreInvalidEndpoint(true);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Expression that computes the endpoint uri to use as the resource endpoint to enrich from
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getAggregationStrategyRef() {
        return aggregationStrategyRef;
    }

    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.aggregationStrategyRef = aggregationStrategyRef;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public Boolean getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    public void setAggregationStrategyMethodAllowNull(Boolean aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Boolean getAggregateOnException() {
        return aggregateOnException;
    }

    public void setAggregateOnException(Boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public Boolean getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(Boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }
}