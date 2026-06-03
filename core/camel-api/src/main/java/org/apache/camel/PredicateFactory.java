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
package org.apache.camel;

/**
 * Deferred factory for creating a {@link Predicate} within a {@link CamelContext}.
 * <p/>
 * Being a {@link FunctionalInterface}, implementations are typically supplied as lambdas or method references wherever
 * a context-aware predicate must be constructed lazily, for example when wiring filter or choice EIP definitions during
 * route startup. The factory receives the context so it can resolve language services or configuration values needed to
 * build the predicate.
 *
 * @see   Predicate
 * @see   ExpressionFactory
 * @since 3.8
 */
@FunctionalInterface
public interface PredicateFactory {

    /**
     * Creates a predicate
     *
     * @param  camelContext the camel context
     * @return              the created predicate.
     */
    Predicate createPredicate(CamelContext camelContext);

}
