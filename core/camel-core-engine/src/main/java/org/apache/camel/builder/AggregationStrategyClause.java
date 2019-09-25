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
package org.apache.camel.builder;

import java.util.function.BiFunction;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

public class AggregationStrategyClause<T> implements AggregationStrategy {
    private final T parent;
    private AggregationStrategy strategy;

    public AggregationStrategyClause(T parent) {
        this.parent = parent;
        this.strategy = null;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        return ObjectHelper.notNull(strategy, "AggregationStrategy").aggregate(oldExchange, newExchange);
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange, Exchange inputExchange) {
        return ObjectHelper.notNull(strategy, "AggregationStrategy").aggregate(oldExchange, newExchange, inputExchange);
    }

    // *******************************
    // Exchange
    // *******************************

    /**
     * Define an aggregation strategy which targets the exchnage.
     */
    public T exchange(final BiFunction<Exchange, Exchange, Exchange> function) {
        strategy = function::apply;
        return parent;
    }

    // *******************************
    // Message
    // *******************************

    /**
     * Define an aggregation strategy which targets Exchanges In Message.
     * <blockquote>
     * 
     * <pre>
     * {@code
     * from("direct:aggregate")
     *     .aggregate()
     *         .message((old, new) -> {
     *             if (old == null) {
     *                 return new;
     *             }
     *
     *             String oldBody = old.getBody(String.class);
     *             String newBody = new.getBody(String.class);
     *
     *             old.setBody(oldBody + "+" + newBody);
     *
     *             return old;
     *         });
     * }
     * </pre>
     * 
     * </blockquote>
     */
    public T message(final BiFunction<Message, Message, Message> function) {
        return exchange((Exchange oldExchange, Exchange newExchange) -> {
            Message oldMessage = oldExchange != null ? oldExchange.getIn() : null;
            Message newMessage = ObjectHelper.notNull(newExchange, "NewExchange").getIn();
            Message result = function.apply(oldMessage, newMessage);

            if (oldExchange != null) {
                oldExchange.setIn(result);
                return oldExchange;
            } else {
                newExchange.setIn(result);
                return newExchange;
            }
        });
    }

    // *******************************
    // Body
    // *******************************

    /**
     * Define an aggregation strategy which targets Exchanges In Body.
     * <blockquote>
     * 
     * <pre>
     * {@code
     * from("direct:aggregate")
     *     .aggregate()
     *         .body((old, new) -> {
     *             if (old == null) {
     *                 return new;
     *             }
     *
     *             return old.toString() + new.toString();
     *         });
     * }
     * </pre>
     * 
     * </blockquote>
     */
    public T body(final BiFunction<Object, Object, Object> function) {
        return body(Object.class, function);
    }

    /**
     * Define an aggregation strategy which targets Exchanges In Body.
     * <blockquote>
     * 
     * <pre>
     * {@code
     * from("direct:aggregate")
     *     .aggregate()
     *         .body(String.class, (old, new) -> {
     *             if (old == null) {
     *                 return new;
     *             }
     *
     *             return old + new;
     *         });
     * }
     * </pre>
     * 
     * </blockquote>
     */
    public <B> T body(final Class<B> type, final BiFunction<B, B, Object> function) {
        return body(type, type, function);
    }

    /**
     * Define an aggregation strategy which targets Exchanges In Body.
     */
    public <O, N> T body(final Class<O> oldType, final Class<N> newType, final BiFunction<O, N, Object> function) {
        return exchange((Exchange oldExchange, Exchange newExchange) -> {
            Message oldMessage = oldExchange != null ? oldExchange.getIn() : null;
            Message newMessage = ObjectHelper.notNull(newExchange, "NewExchange").getIn();

            Object result = function.apply(oldMessage != null ? oldMessage.getBody(oldType) : null, newMessage != null ? newMessage.getBody(newType) : null);

            if (oldExchange != null) {
                oldExchange.getIn().setBody(result);
                return oldExchange;
            } else {
                newExchange.getIn().setBody(result);
                return newExchange;
            }
        });
    }
}
