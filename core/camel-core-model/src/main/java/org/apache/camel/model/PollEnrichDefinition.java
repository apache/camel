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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Enriches messages with data polled from a secondary resource
 *
 * @see org.apache.camel.processor.Enricher
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "pollEnrich")
@XmlAccessorType(XmlAccessType.FIELD)
public class PollEnrichDefinition extends ExpressionNode implements AggregationStrategyAwareDefinition<PollEnrichDefinition> {

    @XmlTransient
    private AggregationStrategy aggregationStrategyBean;

    @XmlAttribute
    private String variableReceive;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.AggregationStrategy")
    private String aggregationStrategy;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String aggregationStrategyMethodName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String aggregationStrategyMethodAllowNull;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String aggregateOnException;
    @XmlAttribute
    @Metadata(javaType = "java.time.Duration", defaultValue = "-1")
    private String timeout;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer")
    private String cacheSize;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreInvalidEndpoint;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String autoStartComponents;

    public PollEnrichDefinition() {
    }

    public PollEnrichDefinition(AggregationStrategy aggregationStrategy, long timeout) {
        this.aggregationStrategyBean = aggregationStrategy;
        this.timeout = Long.toString(timeout);
    }

    @Override
    public String toString() {
        return "PollEnrich[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "pollEnrich";
    }

    @Override
    public String getLabel() {
        return "pollEnrich[" + getExpression() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Timeout in millis when polling from the external service.
     * <p/>
     * The timeout has influence about the poll enrich behavior. It basically operations in three different modes:
     * <ul>
     * <li>negative value - Waits until a message is available and then returns it. Warning that this method could block
     * indefinitely if no messages are available.</li>
     * <li>0 - Attempts to receive a message exchange immediately without waiting and returning <tt>null</tt> if a
     * message exchange is not available yet.</li>
     * <li>positive value - Attempts to receive a message exchange, waiting up to the given timeout to expire if a
     * message is not yet available. Returns <tt>null</tt> if timed out</li>
     * </ul>
     * The default value is -1 and therefore the method could block indefinitely, and therefore its recommended to use a
     * timeout value
     */
    public PollEnrichDefinition timeout(long timeout) {
        setTimeout(Long.toString(timeout));
        return this;
    }

    /**
     * Timeout in millis when polling from the external service.
     * <p/>
     * The timeout has influence about the poll enrich behavior. It basically operations in three different modes:
     * <ul>
     * <li>negative value - Waits until a message is available and then returns it. Warning that this method could block
     * indefinitely if no messages are available.</li>
     * <li>0 - Attempts to receive a message exchange immediately without waiting and returning <tt>null</tt> if a
     * message exchange is not available yet.</li>
     * <li>positive value - Attempts to receive a message exchange, waiting up to the given timeout to expire if a
     * message is not yet available. Returns <tt>null</tt> if timed out</li>
     * </ul>
     * The default value is -1 and therefore the method could block indefinitely, and therefore its recommended to use a
     * timeout value
     */
    public PollEnrichDefinition timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * To use a variable to store the received message body (only body, not headers). This is handy for easy access to
     * the received message body via variables.
     *
     * Important: When using receive variable then the received body is stored only in this variable and <b>not</b> on
     * the current {@link org.apache.camel.Message}.
     */
    public PollEnrichDefinition variableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
        return this;
    }

    /**
     * Sets the AggregationStrategy to be used to merge the reply from the external service, into a single outgoing
     * message. By default Camel will use the reply from the external service as outgoing message.
     */
    @Override
    public PollEnrichDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategyBean = aggregationStrategy;
        return this;
    }

    /**
     * Refers to an AggregationStrategy to be used to merge the reply from the external service, into a single outgoing
     * message. By default Camel will use the reply from the external service as outgoing message.
     */
    @Override
    public PollEnrichDefinition aggregationStrategy(String aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
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
     * If this option is false then the aggregate method is not used if there was no data to enrich. If this option is
     * true then null values is used as the oldExchange (when no data to enrich), when using POJOs as the
     * AggregationStrategy.
     */
    public PollEnrichDefinition aggregationStrategyMethodAllowNull(boolean aggregationStrategyMethodAllowNull) {
        setAggregationStrategyMethodAllowNull(Boolean.toString(aggregationStrategyMethodAllowNull));
        return this;
    }

    /**
     * If this option is false then the aggregate method is not used if there was an exception thrown while trying to
     * retrieve the data to enrich from the resource. Setting this option to true allows end users to control what to do
     * if there was an exception in the aggregate method. For example to suppress the exception or set a custom message
     * body etc.
     */
    public PollEnrichDefinition aggregateOnException(boolean aggregateOnException) {
        setAggregateOnException(Boolean.toString(aggregateOnException));
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ConsumerCache} which is used to cache and reuse
     * consumers when uris are reused.
     *
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized. If each dynamic
     * endpoint is unique then its best to turn off caching by setting this to -1, which allows Camel to not cache both
     * the producers and endpoints; they are regarded as prototype scoped and will be stopped and discarded after use.
     * This reduces memory usage as otherwise producers/endpoints are stored in memory in the caches.
     *
     * However if there are a high degree of dynamic endpoints that have been used before, then it can benefit to use
     * the cache to reuse both producers and endpoints and therefore the cache size can be set accordingly or rely on
     * the default size (1000).
     *
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size can help
     * reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param  cacheSize the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return           the builder
     */
    public PollEnrichDefinition cacheSize(int cacheSize) {
        setCacheSize(Integer.toString(cacheSize));
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ConsumerCache} which is used to cache and reuse
     * consumers when uris are reused.
     *
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized. If each dynamic
     * endpoint is unique then its best to turn off caching by setting this to -1, which allows Camel to not cache both
     * the producers and endpoints; they are regarded as prototype scoped and will be stopped and discarded after use.
     * This reduces memory usage as otherwise producers/endpoints are stored in memory in the caches.
     *
     * However if there are a high degree of dynamic endpoints that have been used before, then it can benefit to use
     * the cache to reuse both producers and endpoints and therefore the cache size can be set accordingly or rely on
     * the default size (1000).
     *
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size can help
     * reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param  cacheSize the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return           the builder
     */
    public PollEnrichDefinition cacheSize(String cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public PollEnrichDefinition ignoreInvalidEndpoint() {
        setIgnoreInvalidEndpoint(Boolean.toString(true));
        return this;
    }

    /**
     * Whether to auto startup components when poll enricher is starting up.
     *
     * @return the builder
     */
    public PollEnrichDefinition autoStartComponents(String autoStartComponents) {
        setAutoStartComponents(autoStartComponents);
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

    @Override
    public AggregationStrategy getAggregationStrategyBean() {
        return aggregationStrategyBean;
    }

    @Override
    public String getAggregationStrategyRef() {
        return aggregationStrategy;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getVariableReceive() {
        return variableReceive;
    }

    public void setVariableReceive(String variableReceive) {
        this.variableReceive = variableReceive;
    }

    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategyBean = aggregationStrategy;
    }

    public String getAggregationStrategyMethodName() {
        return aggregationStrategyMethodName;
    }

    public void setAggregationStrategyMethodName(String aggregationStrategyMethodName) {
        this.aggregationStrategyMethodName = aggregationStrategyMethodName;
    }

    public String getAggregationStrategyMethodAllowNull() {
        return aggregationStrategyMethodAllowNull;
    }

    public void setAggregationStrategyMethodAllowNull(String aggregationStrategyMethodAllowNull) {
        this.aggregationStrategyMethodAllowNull = aggregationStrategyMethodAllowNull;
    }

    public String getAggregateOnException() {
        return aggregateOnException;
    }

    public void setAggregateOnException(String aggregateOnException) {
        this.aggregateOnException = aggregateOnException;
    }

    public String getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(String cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(String ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    public String getAutoStartComponents() {
        return autoStartComponents;
    }

    public void setAutoStartComponents(String autoStartComponents) {
        this.autoStartComponents = autoStartComponents;
    }
}
