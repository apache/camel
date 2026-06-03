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
 * Implemented by objects that wish to receive a reference to the {@link Route} they belong to.
 * <p/>
 * The framework injects the route after it is built but before consumers are started. The primary implementor is
 * {@link Consumer}: the consumer attached to a route's input {@link Endpoint} is set as route-aware so that it can
 * access route-level metadata (id, policy, etc.) at runtime.
 * <p/>
 * Custom {@link Processor}s and services may also implement this interface to obtain contextual route information
 * without carrying an explicit route reference in their constructor.
 *
 * @see Route
 * @see Consumer
 */
public interface RouteAware {

    /**
     * Injects the {@link Route}
     *
     * @param route the route
     */
    void setRoute(@Nullable Route route);

    /**
     * Gets the {@link Route}
     *
     * @return the route, or <tt>null</tt> if no route has been set.
     */
    @Nullable
    Route getRoute();

}
