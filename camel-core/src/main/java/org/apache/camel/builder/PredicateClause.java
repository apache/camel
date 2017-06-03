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
package org.apache.camel.builder;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class PredicateClause<T> implements org.apache.camel.Predicate {
    private final T parent;
    private Predicate<Exchange> predicate;

    public PredicateClause(T parent) {
        this.parent = parent;
        this.predicate = null;
    }

    @Override
    public boolean matches(Exchange exchange) {
        return (predicate != null) ?  predicate.test(exchange) : false;
    }

    // *******************************
    // Exchange
    // *******************************

    /**
     * Define a {@link org.apache.camel.Predicate} which targets the Exchange.
     */
    public T exchange(final Predicate<Exchange> predicate) {
        this.predicate = predicate::test;
        return parent;
    }


    // *******************************
    // Message
    // *******************************

    /**
     * Define a {@link org.apache.camel.Predicate} which targets the Exchange In Message.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .choice()
     *         .when()
     *            .message(m -> m.getBody() != null)
     *            .log("Received ${body}")
     *     .endChoice()
     * }</pre></blockquote>
     */
    public T message(final Predicate<Message> predicate) {
        return exchange(e -> predicate.test(e.getIn()));
    }

    // *******************************
    // Body
    // *******************************


    /**
     * Define a {@link org.apache.camel.Predicate} which targets the Exchange In Body.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .choice()
     *         .when()
     *            .body(b -> b != null)
     *            .log("Received ${body}")
     *     .endChoice()
     * }</pre></blockquote>
     */
    public T body(final Predicate<Object> predicate) {
        return exchange(e -> predicate.test(e.getIn().getBody()));
    }

    /**
     * Define a {@link org.apache.camel.Predicate} which targets the typed Exchange In Body.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .choice()
     *         .when()
     *            .body(Long.class, b -> (b & 1) == 0)
     *            .log("Received even number ${body}")
     *     .endChoice()
     * }</pre></blockquote>
     */
    public <B> T body(final Class<B> type, final Predicate<B> predicate) {
        return exchange(e -> predicate.test(e.getIn().getBody(type)));
    }

    /**
     * Define a {@link org.apache.camel.Predicate} which targets the Exchange In Body and its Headers.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .choice()
     *         .when()
     *            .body((b, h) -> b != null || h.containsKy("ToProcess"))
     *            .log("Received ${body}")
     *     .endChoice()
     * }</pre></blockquote>
     */
    public T body(final BiPredicate<Object, Map<String, Object>> predicate) {
        return exchange(e -> predicate.test(
            e.getIn().getBody(),
            e.getIn().getHeaders())
        );
    }

    /**
     * Define a {@link org.apache.camel.Predicate} which targets the typed Exchange In Body and its Headers.
     *     
     * <blockquote><pre>{@code
     * from("direct:aggregate")
     *     .choice()
     *         .when()
     *            .body(String.class, (b, h) -> b != null && !b.isEmpty() || h.containsKy("ToProcess"))
     *            .log("Received ${body}")
     *     .endChoice()
     * }</pre></blockquote>
     */
    public <B> T body(final Class<B> type, final BiPredicate<B, Map<String, Object>> predicate) {
        return exchange(e -> predicate.test(
            e.getIn().getBody(type),
            e.getIn().getHeaders())
        );
    }
}
