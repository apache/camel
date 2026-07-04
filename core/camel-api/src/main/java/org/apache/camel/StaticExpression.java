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

import org.jspecify.annotations.Nullable;

/**
 * Marker for an {@link Expression} whose value is a compile-time or configuration-time constant.
 * <p/>
 * A static expression always evaluates to the same result regardless of the {@link Exchange}, so the framework can
 * optimize it: it is evaluated once, the result is cached, and no per-exchange evaluation overhead is incurred.
 * Implementations must expose the constant through {@link #getValue()} and allow it to be overwritten via
 * {@link #setValue(Object)} (for example, when a property placeholder is resolved at startup).
 *
 * @see   Expression
 * @see   Predicate
 * @since 3.7
 */
public interface StaticExpression extends Expression {

    /**
     * Gets the constant value
     */
    @Nullable
    Object getValue();

    /**
     * Sets the constant value
     */
    void setValue(@Nullable Object value);

}
