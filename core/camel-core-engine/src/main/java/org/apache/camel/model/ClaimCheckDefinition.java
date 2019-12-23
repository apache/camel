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

import java.util.function.Supplier;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.spi.Metadata;

/**
 * The Claim Check EIP allows you to replace message content with a claim check
 * (a unique key), which can be used to retrieve the message content at a later
 * time.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "claimCheck")
@XmlAccessorType(XmlAccessType.FIELD)
public class ClaimCheckDefinition extends NoOutputDefinition<ClaimCheckDefinition> {

    @XmlAttribute(required = true)
    @Metadata(enums = "Get,GetAndRemove,Set,Push,Pop", javaType = "org.apache.camel.model.ClaimCheckOperation")
    private String operation;
    @XmlAttribute
    private String key;
    @XmlAttribute
    private String filter;
    @XmlAttribute(name = "strategyRef")
    @Metadata(label = "advanced")
    private String aggregationStrategyRef;
    @XmlAttribute(name = "strategyMethodName")
    @Metadata(label = "advanced")
    private String aggregationStrategyMethodName;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;

    public ClaimCheckDefinition() {
    }

    @Override
    public String toString() {
        if (operation != null) {
            return "ClaimCheck[" + operation + "]";
        } else {
            return "ClaimCheck";
        }
    }

    @Override
    public String getShortName() {
        return "claimCheck";
    }

    @Override
    public String getLabel() {
        return "claimCheck";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * The claim check operation to use. The following operations is supported:
     * <ul>
     * <li>Get</li> - Gets (does not remove) the claim check by the given key.
     * <li>GetAndRemove</li> - Gets and remove the claim check by the given key.
     * <li>Set</li> - Sets a new (will override if key already exists) claim
     * check with the given key.
     * <li>Push</li> - Sets a new claim check on the stack (does not use key).
     * <li>Pop</li> - Gets the latest claim check from the stack (does not use
     * key).
     * </ul>
     */
    public ClaimCheckDefinition operation(ClaimCheckOperation operation) {
        return operation(operation.name());
    }

    /**
     * The claim check operation to use. The following operations is supported:
     * <ul>
     * <li>Get</li> - Gets (does not remove) the claim check by the given key.
     * <li>GetAndRemove</li> - Gets and remove the claim check by the given key.
     * <li>Set</li> - Sets a new (will override if key already exists) claim
     * check with the given key.
     * <li>Push</li> - Sets a new claim check on the stack (does not use key).
     * <li>Pop</li> - Gets the latest claim check from the stack (does not use
     * key).
     * </ul>
     */
    public ClaimCheckDefinition operation(String operation) {
        setOperation(operation);
        return this;
    }

    /**
     * To use a specific key for claim check id (for dynamic keys use simple
     * language syntax as the key).
     */
    public ClaimCheckDefinition key(String key) {
        setKey(key);
        return this;
    }

    /**
     * Specified a filter to control what data gets merging data back from the
     * claim check repository. The following syntax is supported:
     * <ul>
     * <li>body</li> - to aggregate the message body
     * <li>attachments</li> - to aggregate all the message attachments
     * <li>headers</li> - to aggregate all the message headers
     * <li>header:pattern</li> - to aggregate all the message headers that
     * matches the pattern.
     * </ul>
     * The pattern uses the following rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the
     * pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     * <p>
     * You can specify multiple rules separated by comma. For example to include
     * the message body and all headers starting with foo
     * <tt>body,header:foo*</tt>. The syntax supports the following prefixes
     * which can be used to specify include,exclude, or remove
     * <ul>
     * <li>+</li> - to include (which is the default mode)
     * <li>-</li> - to exclude (exclude takes precedence over include)
     * <li>--</li> - to remove (remove takes precedence)
     * </ul>
     * For example to exclude a header name foo, and remove all headers starting
     * with bar <tt>-header:foo,--headers:bar*</tt> Note you cannot have both
     * include and exclude <tt>header:pattern</tt> at the same time.
     */
    public ClaimCheckDefinition filter(String filter) {
        setFilter(filter);
        return this;
    }

    /**
     * To use a custom {@link AggregationStrategy} instead of the default
     * implementation. Notice you cannot use both custom aggregation strategy
     * and configure data at the same time.
     */
    public ClaimCheckDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * To use a custom {@link AggregationStrategy} instead of the default
     * implementation. Notice you cannot use both custom aggregation strategy
     * and configure data at the same time.
     */
    public ClaimCheckDefinition aggregationStrategy(Supplier<AggregationStrategy> aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy.get());
        return this;
    }

    /**
     * To use a custom {@link AggregationStrategy} instead of the default
     * implementation. Notice you cannot use both custom aggregation strategy
     * and configure data at the same time.
     */
    public ClaimCheckDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setAggregationStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * This option can be used to explicit declare the method name to use, when
     * using POJOs as the AggregationStrategy.
     */
    public ClaimCheckDefinition aggregationStrategyMethodName(String aggregationStrategyMethodName) {
        setAggregationStrategyMethodName(aggregationStrategyMethodName);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
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

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}
