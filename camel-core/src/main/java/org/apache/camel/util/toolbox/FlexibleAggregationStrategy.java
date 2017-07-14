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

import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.CompletionAwareAggregationStrategy;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Flexible Aggregation Strategy is a highly customizable, fluently configurable aggregation strategy. It allows you to quickly 
 * allows you to quickly whip up an {@link AggregationStrategy} that is capable of performing the most typical aggregation duties, 
 * with zero Java code. 
 * <p/>
 * It can perform the following logic:
 * <ul>
 *   <li>Filtering results based on a defined {@link Predicate} written in any language, such as XPath, OGNL, Simple, Javascript, etc.</li>
 *   <li>Picking specific data elements for aggregation.</li>
 *   <li>Accumulating results in any designated {@link Collection} type, e.g. in a HashSet, LinkedList, ArrayList, etc.</li>
 *   <li>Storing the output in a specific place in the Exchange: a property, a header or in the body.</li>
 * </ul>
 * 
 * It also includes the ability to specify both aggregation batch completion actions and timeout actions, in an abbreviated manner.
 * <p/>
 * This Aggregation Strategy is suitable for usage in aggregate, split, multicast, enrich and recipient list EIPs.
 * 
 */
public class FlexibleAggregationStrategy<E extends Object> implements AggregationStrategy, 
        CompletionAwareAggregationStrategy, TimeoutAwareAggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(FlexibleAggregationStrategy.class);

    private Expression pickExpression = ExpressionBuilder.bodyExpression();
    private Predicate conditionPredicate;
    @SuppressWarnings("rawtypes")
    private Class<? extends Collection> collectionType;
    @SuppressWarnings("unchecked")
    private Class<E> castAs = (Class<E>) Object.class;
    private boolean storeNulls;
    private boolean ignoreInvalidCasts; // = false
    private FlexibleAggregationStrategyInjector injector = new BodyInjector(castAs);
    private TimeoutAwareMixin timeoutMixin;
    private CompletionAwareMixin completionMixin;

    /**
     * Initializes a new instance with {@link Object} as the {@link FlexibleAggregationStrategy#castAs} type.
     */
    public FlexibleAggregationStrategy() {
    }
    
    /**
     * Initializes a new instance with the specified type as the {@link FlexibleAggregationStrategy#castAs} type.
     * @param type The castAs type.
     */
    public FlexibleAggregationStrategy(Class<E> type) {
        this.castAs = type;
    }
    
    /**
     * Set an expression to extract the element to be aggregated from the incoming {@link Exchange}.
     * All results are cast to the {@link FlexibleAggregationStrategy#castAs} type (or the type specified in the constructor).
     * <p/>
     * By default, it picks the full IN message body of the incoming exchange. 
     * @param expression The picking expression.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> pick(Expression expression) {
        this.pickExpression = expression;
        return this;
    }

    /**
     * Set a filter condition such as only results satisfying it will be aggregated. 
     * By default, all picked values will be processed.
     * @param predicate The condition.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> condition(Predicate predicate) {
        this.conditionPredicate = predicate;
        return this;
    }

    /**
     * Accumulate the result of the <i>pick expression</i> in a collection of the designated type. 
     * No <tt>null</tt>s will stored unless the {@link FlexibleAggregationStrategy#storeNulls()} option is enabled.
     * @param collectionType The type of the Collection to aggregate into.
     * @return This instance.
     */
    @SuppressWarnings("rawtypes")
    public FlexibleAggregationStrategy<E> accumulateInCollection(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
        return this;
    }

    /**
     * Store the result of this Aggregation Strategy (whether an atomic element or a Collection) in a property with
     * the designated name.
     * @param propertyName The property name.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> storeInProperty(String propertyName) {
        this.injector = new PropertyInjector(castAs, propertyName);
        return this;
    }

    /**
     * Store the result of this Aggregation Strategy (whether an atomic element or a Collection) in an IN message header with
     * the designated name.
     * @param headerName The header name.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> storeInHeader(String headerName) {
        this.injector = new HeaderInjector(castAs, headerName);
        return this;
    }

    /**
     * Store the result of this Aggregation Strategy (whether an atomic element or a Collection) in the body of the IN message.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> storeInBody() {
        this.injector = new BodyInjector(castAs);
        return this;
    }

    /**
     * Cast the result of the <i>pick expression</i> to this type.
     * @param castAs Type for the cast.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> castAs(Class<E> castAs) {
        this.castAs = castAs;
        injector.setType(castAs);
        return this;
    }

    /**
     * Enables storing null values in the resulting collection.
     * By default, this aggregation strategy will drop null values.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> storeNulls() {
        this.storeNulls = true;
        return this;
    }
    
    /**
     * Ignores invalid casts instead of throwing an exception if the <i>pick expression</i> result cannot be casted to the 
     * specified type.
     * By default, this aggregation strategy will throw an exception if an invalid cast occurs.
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> ignoreInvalidCasts() {
        this.ignoreInvalidCasts = true;
        return this;
    }
    
    /**
     * Plugs in logic to execute when a timeout occurs.
     * @param timeoutMixin
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> timeoutAware(TimeoutAwareMixin timeoutMixin) {
        this.timeoutMixin = timeoutMixin;
        return this;
    }

    /**
     * Plugs in logic to execute when an aggregation batch completes.
     * @param completionMixin
     * @return This instance.
     */
    public FlexibleAggregationStrategy<E> completionAware(CompletionAwareMixin completionMixin) {
        this.completionMixin = completionMixin;
        return this;
    }
    
    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Exchange exchange = oldExchange;
        if (exchange == null) {
            exchange = ExchangeHelper.createCorrelatedCopy(newExchange, true);
            injector.prepareAggregationExchange(exchange);
        }

        // 1. Apply the condition and reject the aggregation if unmatched
        if (conditionPredicate != null && !conditionPredicate.matches(newExchange)) {
            LOG.trace("Dropped exchange {} from aggregation as predicate {} was not matched", newExchange, conditionPredicate);
            return exchange;
        }

        // 2. Pick the appropriate element of the incoming message, casting it to the specified class
        //    If null, act accordingly based on storeNulls
        E picked = null;
        try {
            picked = pickExpression.evaluate(newExchange, castAs);
        } catch (TypeConversionException exception) {
            if (!ignoreInvalidCasts) {
                throw exception;
            }
        }
        
        if (picked == null && !storeNulls) {
            LOG.trace("Dropped exchange {} from aggregation as pick expression returned null and storing nulls is not enabled", newExchange);
            return exchange;
        }

        if (collectionType == null) {
            injectAsRawValue(exchange, picked);
        } else {
            injectAsCollection(exchange, picked, collectionType);
        }

        return exchange;
    }
    

    @Override
    public void timeout(Exchange oldExchange, int index, int total, long timeout) {
        if (timeoutMixin == null) {
            return;
        }
        timeoutMixin.timeout(oldExchange, index, total, timeout);
    }

    @Override
    public void onCompletion(Exchange exchange) {
        if (completionMixin == null) {
            return;
        }
        completionMixin.onCompletion(exchange);
    }

    private void injectAsRawValue(Exchange oldExchange, E picked) {
        injector.setValue(oldExchange, picked);
    }

    private void injectAsCollection(Exchange oldExchange, E picked, Class<? extends Collection> collectionType) {
        Collection<E> col = injector.getValueAsCollection(oldExchange, collectionType);
        col = safeInsertIntoCollection(oldExchange, col, picked);
        injector.setValueAsCollection(oldExchange, col);
    }

    @SuppressWarnings("unchecked")
    private Collection<E> safeInsertIntoCollection(Exchange oldExchange, Collection<E> oldValue, E toInsert) {
        Collection<E> collection = null;
        try {
            if (oldValue == null || oldExchange.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD, Boolean.class) == null) {
                try {
                    collection = collectionType.newInstance();
                } catch (Exception e) {
                    LOG.warn("Could not instantiate collection of type {}. Aborting aggregation.", collectionType);
                    throw ObjectHelper.wrapCamelExecutionException(oldExchange, e);
                }
                oldExchange.setProperty(Exchange.AGGREGATED_COLLECTION_GUARD, Boolean.FALSE);
            } else {
                collection = collectionType.cast(oldValue);
            }
            
            if (collection != null) {
                collection.add(toInsert);
            }
            
        } catch (ClassCastException exception) {
            if (!ignoreInvalidCasts) {
                throw exception;
            }
        }
        return collection;
    }
    
    public interface TimeoutAwareMixin {
        void timeout(Exchange exchange, int index, int total, long timeout);
    }
    
    public interface CompletionAwareMixin {
        void onCompletion(Exchange exchange);
    }
    
    private abstract class FlexibleAggregationStrategyInjector {
        protected Class<E> type;
        
        FlexibleAggregationStrategyInjector(Class<E> type) {
            this.type = type;
        }
        
        public void setType(Class<E> type) {
            this.type = type;
        }
        
        public abstract void prepareAggregationExchange(Exchange exchange);
        public abstract E getValue(Exchange exchange);
        public abstract void setValue(Exchange exchange, E obj);
        public abstract Collection<E> getValueAsCollection(Exchange exchange, Class<? extends Collection> type);
        public abstract void setValueAsCollection(Exchange exchange, Collection<E> obj);
    }
    
    private class PropertyInjector extends FlexibleAggregationStrategyInjector {
        private String propertyName;
        
        PropertyInjector(Class<E> type, String propertyName) {
            super(type);
            this.propertyName = propertyName;
        }
        
        @Override
        public void prepareAggregationExchange(Exchange exchange) {
            exchange.removeProperty(propertyName);
        }
        
        @Override
        public E getValue(Exchange exchange) {
            return exchange.getProperty(propertyName, type);
        }

        @Override
        public void setValue(Exchange exchange, E obj) {
            exchange.setProperty(propertyName, obj);
        }

        @Override @SuppressWarnings("unchecked")
        public Collection<E> getValueAsCollection(Exchange exchange, Class<? extends Collection> type) {
            Object value = exchange.getProperty(propertyName);
            if (value == null) {
                // empty so create a new collection to host this
                return exchange.getContext().getInjector().newInstance(type);
            } else {
                return exchange.getProperty(propertyName, type);
            }
        }

        @Override
        public void setValueAsCollection(Exchange exchange, Collection<E> obj) {
            exchange.setProperty(propertyName, obj);
        }

    }
    
    private class HeaderInjector extends FlexibleAggregationStrategyInjector {
        private String headerName;
        
        HeaderInjector(Class<E> type, String headerName) {
            super(type);
            this.headerName = headerName;
        }
        
        @Override
        public void prepareAggregationExchange(Exchange exchange) {
            exchange.getIn().removeHeader(headerName);
        }
        
        @Override
        public E getValue(Exchange exchange) {
            return exchange.getIn().getHeader(headerName, type);
        }

        @Override
        public void setValue(Exchange exchange, E obj) {
            exchange.getIn().setHeader(headerName, obj);
        }

        @Override @SuppressWarnings("unchecked")
        public Collection<E> getValueAsCollection(Exchange exchange, Class<? extends Collection> type) {
            Object value = exchange.getIn().getHeader(headerName);
            if (value == null) {
                // empty so create a new collection to host this
                return exchange.getContext().getInjector().newInstance(type);
            } else {
                return exchange.getIn().getHeader(headerName, type);
            }
        }
        
        @Override
        public void setValueAsCollection(Exchange exchange, Collection<E> obj) {
            exchange.getIn().setHeader(headerName, obj);
        }
    }
    
    private class BodyInjector extends FlexibleAggregationStrategyInjector {
        BodyInjector(Class<E> type) {
            super(type);
        }

        @Override
        public void prepareAggregationExchange(Exchange exchange) {
            exchange.getIn().setBody(null);
        }
        
        @Override
        public E getValue(Exchange exchange) {
            return exchange.getIn().getBody(type);
        }

        @Override
        public void setValue(Exchange exchange, E obj) {
            exchange.getIn().setBody(obj);
        }

        @Override @SuppressWarnings("unchecked")
        public Collection<E> getValueAsCollection(Exchange exchange, Class<? extends Collection> type) {
            Object value = exchange.getIn().getBody();
            if (value == null) {
                // empty so create a new collection to host this
                return exchange.getContext().getInjector().newInstance(type);
            } else {
                return exchange.getIn().getBody(type);
            }
        }
        
        @Override
        public void setValueAsCollection(Exchange exchange, Collection<E> obj) {
            exchange.getIn().setBody(obj);
        }
    }
    
}
