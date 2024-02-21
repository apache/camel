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

import org.apache.camel.AggregationStrategy;

/**
 * Enables definitions to support {@link org.apache.camel.AggregationStrategy}
 */
public interface AggregationStrategyAwareDefinition<Type extends ProcessorDefinition<?>> {

    /**
     * Sets the aggregation strategy to use.
     *
     * @param  aggregationStrategy the aggregation strategy
     * @return                     the builder
     */
    Type aggregationStrategy(AggregationStrategy aggregationStrategy);

    /**
     * Sets the aggregation strategy to use.
     *
     * @param  aggregationStrategy the aggregation strategy
     * @return                     the builder
     */
    Type aggregationStrategy(String aggregationStrategy);

    /**
     * Gets the aggregation strategy
     */
    AggregationStrategy getAggregationStrategyBean();

    /**
     * Gets a reference id to lookup the aggregation strategy from the registry
     */
    String getAggregationStrategyRef();

    /**
     * This option can be used to explicit declare the method name to use, when using beans as the AggregationStrategy.
     */
    String getAggregationStrategyMethodName();

    /**
     * If this option is false then the aggregate method is not used for the very first aggregation. If this option is
     * true then null values is used as the oldExchange (at the very first aggregation), when using beans as the
     * AggregationStrategy.
     */
    String getAggregationStrategyMethodAllowNull();

}
