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
 * Generic builder contract for objects that are assembled in multiple steps before being produced.
 * <p/>
 * Implementations collect configuration through setters or a fluent API and then materialize the final object via
 * {@link #build()}. Being a {@link FunctionalInterface}, it can also be satisfied by a lambda or method reference
 * wherever a deferred-construction callback is expected (for example in route template bean suppliers).
 *
 * @param <T> the type of object produced by this builder
 * @see       RouteTemplateContext.BeanSupplier
 */
@FunctionalInterface
public interface Builder<T> {
    T build();
}
