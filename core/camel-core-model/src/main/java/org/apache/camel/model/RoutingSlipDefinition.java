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

import java.util.Collections;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.spi.Metadata;

/**
 * Routes a message through a series of steps that are pre-determined (the slip)
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "routingSlip")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutingSlipDefinition<Type extends ProcessorDefinition<Type>> extends ExpressionNode {

    public static final String DEFAULT_DELIMITER = ",";

    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String uriDelimiter;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreInvalidEndpoints;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer")
    private String cacheSize;

    public RoutingSlipDefinition() {
        if (uriDelimiter == null) {
            setUriDelimiter(DEFAULT_DELIMITER);
        } else {
            setUriDelimiter(uriDelimiter);
        }
    }

    public RoutingSlipDefinition(String headerName) {
        this(headerName, DEFAULT_DELIMITER);
    }

    public RoutingSlipDefinition(String headerName, String uriDelimiter) {
        super(new HeaderExpression(headerName));
        setUriDelimiter(uriDelimiter);
    }

    public RoutingSlipDefinition(Expression expression, String uriDelimiter) {
        super(expression);
        setUriDelimiter(uriDelimiter);
    }

    public RoutingSlipDefinition(Expression expression) {
        this(expression, DEFAULT_DELIMITER);
    }

    @Override
    public String toString() {
        return "RoutingSlip[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "routingSlip";
    }

    @Override
    public String getLabel() {
        return "routingSlip[" + getExpression() + "]";
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

    /**
     * Expression to define the routing slip, which defines which endpoints to route the message in a pipeline style.
     * Notice the expression is evaluated once, if you want a more dynamic style, then the dynamic router eip is a
     * better choice.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public void setUriDelimiter(String uriDelimiter) {
        this.uriDelimiter = uriDelimiter;
    }

    public String getUriDelimiter() {
        return uriDelimiter;
    }

    public void setIgnoreInvalidEndpoints(String ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public String getIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public String getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(String cacheSize) {
        this.cacheSize = cacheSize;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RoutingSlipDefinition<Type> ignoreInvalidEndpoints() {
        return ignoreInvalidEndpoints(true);
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RoutingSlipDefinition<Type> ignoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        return ignoreInvalidEndpoints(Boolean.toString(ignoreInvalidEndpoints));
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RoutingSlipDefinition<Type> ignoreInvalidEndpoints(String ignoreInvalidEndpoints) {
        setIgnoreInvalidEndpoints(ignoreInvalidEndpoints);
        return this;
    }

    /**
     * Sets the uri delimiter to use
     *
     * @param  uriDelimiter the delimiter
     * @return              the builder
     */
    public RoutingSlipDefinition<Type> uriDelimiter(String uriDelimiter) {
        setUriDelimiter(uriDelimiter);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used to cache and reuse
     * producers when using this routing slip, when uris are reused.
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
    public RoutingSlipDefinition<Type> cacheSize(int cacheSize) {
        return cacheSize(Integer.toString(cacheSize));
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used to cache and reuse
     * producers when using this routing slip, when uris are reused.
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
    public RoutingSlipDefinition<Type> cacheSize(String cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

}
