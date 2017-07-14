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
package org.apache.camel.util.toolbox;

import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;

/**
 * Toolbox class to create commonly used Aggregation Strategies in a fluent manner.
 * For more information about the supported {@link AggregationStrategy}, see links to the Javadocs of the relevant class below.
 * 
 * @since 2.12
 */
public final class AggregationStrategies {

    private AggregationStrategies() { }

    /**
     * Creates a {@link FlexibleAggregationStrategy} pivoting around a particular type, e.g. it casts all <tt>pick expression</tt> 
     * results to the desired type.
     * 
     * @param type The type the {@link FlexibleAggregationStrategy} deals with.
     */
    public static <T> FlexibleAggregationStrategy<T> flexible(Class<T> type) {
        return new FlexibleAggregationStrategy<T>(type);
    }
    
    /**
     * Creates a {@link FlexibleAggregationStrategy} with no particular type, i.e. performing no casts or type conversion of 
     * <tt>pick expression</tt> results.
     */
    public static FlexibleAggregationStrategy<Object> flexible() {
        return new FlexibleAggregationStrategy<Object>();
    }

    /**
     * Use the latest incoming exchange.
     *
     * @see org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
     */
    public static AggregationStrategy useLatest() {
        return new UseLatestAggregationStrategy();
    }
    
    /**
     * Use the original exchange.
     *
     * @see org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy
     */
    public static AggregationStrategy useOriginal() {
        return new UseOriginalAggregationStrategy();
    }

    /**
     * Creates a {@link GroupedExchangeAggregationStrategy} aggregation strategy.
     */
    public static AggregationStrategy groupedExchange() {
        return new GroupedExchangeAggregationStrategy();
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy bean(Object bean) {
        return new AggregationStrategyBeanAdapter(bean);
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy bean(Object bean, String methodName) {
        return new AggregationStrategyBeanAdapter(bean, methodName);
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy beanAllowNull(Object bean, String methodName) {
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(bean, methodName);
        adapter.setAllowNullOldExchange(true);
        adapter.setAllowNullNewExchange(true);
        return adapter;
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy bean(Class<?> type) {
        return new AggregationStrategyBeanAdapter(type);
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy bean(Class<?> type, String methodName) {
        return new AggregationStrategyBeanAdapter(type, methodName);
    }

    /**
     * Creates a {@link AggregationStrategyBeanAdapter} for using a POJO as the aggregation strategy.
     */
    public static AggregationStrategy beanAllowNull(Class<?> type, String methodName) {
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(type, methodName);
        adapter.setAllowNullOldExchange(true);
        adapter.setAllowNullNewExchange(true);
        return adapter;
    }

    /**
     * Creates a {@link XsltAggregationStrategy} as the aggregation strategy.
     */
    public static XsltAggregationStrategy xslt(String xslFileLocation) {
        return XsltAggregationStrategy.create(xslFileLocation);
    }

}
