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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Enriches messages with data polled from a secondary resource
 *
 * @see org.apache.camel.processor.Enricher
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "pollEnrich")
@XmlAccessorType(XmlAccessType.FIELD)
public class PollEnrichDefinition extends NoOutputDefinition<PollEnrichDefinition> implements EndpointRequiredDefinition {
    @XmlElementRef
    private ExpressionDefinition expression;
    @XmlAttribute(name = "uri")
    private String resourceUri;
    // TODO: For Camel 3.0 we should remove this ref attribute as you can do that in the uri, by prefixing with ref:
    @XmlAttribute(name = "ref")
    @Deprecated
    private String resourceRef;
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

    public PollEnrichDefinition() {
    }

    public PollEnrichDefinition(AggregationStrategy aggregationStrategy, String resourceUri, long timeout) {
        this.aggregationStrategy = aggregationStrategy;
        this.resourceUri = resourceUri;
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "PollEnrich[" + description() + " " + aggregationStrategy + "]";
    }
    
    protected String description() {
        return FromDefinition.description(getResourceUri(), getResourceRef(), (Endpoint) null);
    }

    @Override
    public String getLabel() {
        return "pollEnrich[" + description() + "]";
    }

    @Override
    public String getEndpointUri() {
        if (resourceUri != null) {
            return resourceUri;
        } else {
            return null;
        }
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (ObjectHelper.isEmpty(resourceUri) && ObjectHelper.isEmpty(resourceRef) && expression == null) {
            throw new IllegalArgumentException("Either resourceUri, resourceRef or expression must be configured");
        }

        // lookup endpoint
        PollingConsumer consumer = null;
        if (resourceUri != null) {
            Endpoint endpoint = routeContext.resolveEndpoint(resourceUri);
            consumer = endpoint.createPollingConsumer();
        } else if (resourceRef != null) {
            Endpoint endpoint = routeContext.resolveEndpoint(null, resourceRef);
            consumer = endpoint.createPollingConsumer();
        }

        // if no timeout then we should block, and there use a negative timeout
        long time = timeout != null ? timeout : -1;

        // create the expression if any was configured
        Expression exp = createResourceExpression(routeContext);

        PollEnricher enricher;
        if (exp != null) {
            enricher = new PollEnricher(null, exp, time);
        } else if (consumer != null) {
            enricher = new PollEnricher(null, consumer, time);
        } else {
            throw new IllegalArgumentException("Either resourceUri, resourceRef or expression must be configured");
        }

        AggregationStrategy strategy = createAggregationStrategy(routeContext);
        if (strategy == null) {
            enricher.setDefaultAggregationStrategy();
        } else {
            enricher.setAggregationStrategy(strategy);
        }
        if (getAggregateOnException() != null) {
            enricher.setAggregateOnException(getAggregateOnException());
        }

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

        if (strategy != null && strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    /**
     * Creates the {@link org.apache.camel.Expression} from the expression node to use to compute the endpoint to poll from.
     *
     * @param routeContext  the route context
     * @return the created expression, or <tt>null</tt> if no expression configured
     */
    protected Expression createResourceExpression(RouteContext routeContext) {
        if (expression != null) {
            return expression.createExpression(routeContext);
        } else {
            return null;
        }
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The endpoint uri for the external service to poll enrich from. You must use either uri or ref.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getResourceRef() {
        return resourceRef;
    }

    /**
     * Refers to the endpoint for the external service to poll enrich from. You must use either uri or ref.
     *
     * @deprecated use uri with ref:uri instead
     */
    @Deprecated
    public void setResourceRef(String resourceRef) {
        this.resourceRef = resourceRef;
    }

    public Long getTimeout() {
        return timeout;
    }

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
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getAggregationStrategyRef() {
        return aggregationStrategyRef;
    }

    /**
     * Refers to an AggregationStrategy to be used to merge the reply from the external service, into a single outgoing message.
     * By default Camel will use the reply from the external service as outgoing message.
     */
    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.aggregationStrategyRef = aggregationStrategyRef;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when using POJOs as the AggregationStrategy.
     */
    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public Boolean getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    /**
     * If this option is false then the aggregate method is not used if there was no data to enrich.
     * If this option is true then null values is used as the oldExchange (when no data to enrich),
     * when using POJOs as the AggregationStrategy.
     */
    public void setAggregationStrategyMethodAllowNull(Boolean aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Sets the AggregationStrategy to be used to merge the reply from the external service, into a single outgoing message.
     * By default Camel will use the reply from the external service as outgoing message.
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Boolean getAggregateOnException() {
        return aggregateOnException;
    }

    /**
     * If this option is false then the aggregate method is not used if there was an exception thrown while trying
     * to retrieve the data to enrich from the resource. Setting this option to true allows end users to control what
     * to do if there was an exception in the aggregate method. For example to suppress the exception
     * or set a custom message body etc.
     */
    public void setAggregateOnException(Boolean aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    /**
     * Sets an expression to use for dynamic computing the endpoint to poll from.
     * <p/>
     * If this option is set, then <tt>resourceUri</tt> or <tt>resourceRef</tt> is not in use.
     */
    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }
}