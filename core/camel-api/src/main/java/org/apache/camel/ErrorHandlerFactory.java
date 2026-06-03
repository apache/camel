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
 * Factory that creates and configures an <a href="https://camel.apache.org/manual/error-handler.html">error handler</a>
 * for a {@link Route}.
 * <p/>
 * Each route gets its own private error handler instance to avoid cross-route interference. The factory is therefore
 * cloned once per route via {@link #cloneBuilder()} before the route is started. The resulting
 * {@link org.apache.camel.spi.ErrorHandler} wraps every {@link Processor} in the route's pipeline and intercepts any
 * exception thrown during processing, applying the configured retry, redelivery, and dead-letter-channel policies.
 * <p/>
 * Built-in implementations include the default error handler, the dead letter channel, and the no-error-handler (a
 * pass-through). Route configurations can override the factory used for a set of routes via
 * {@link RouteConfigurationsBuilder}.
 *
 * @see org.apache.camel.spi.ErrorHandler
 * @see RouteConfigurationsBuilder
 */
public interface ErrorHandlerFactory {

    /**
     * Whether this error handler supports transacted exchanges.
     */
    boolean supportTransacted();

    /**
     * Clones this factory so each route has its private builder to use, to avoid changes from one route to influence
     * the others.
     * <p/>
     * This is needed by the current Camel route architecture
     *
     * @return a clone of this factory
     */
    ErrorHandlerFactory cloneBuilder();

}
