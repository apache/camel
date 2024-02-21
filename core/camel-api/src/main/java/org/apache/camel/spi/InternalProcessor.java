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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Route;

/**
 * Internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 * <li>Execute {@link UnitOfWork}</li>
 * <li>Keeping track which route currently is being routed</li>
 * <li>Execute {@link RoutePolicy}</li>
 * <li>Gather JMX performance statics</li>
 * <li>Tracing</li>
 * <li>Debugging</li>
 * <li>Message History</li>
 * <li>Stream Caching</li>
 * <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 */
public interface InternalProcessor extends AsyncProcessor {

    /**
     * Adds an {@link CamelInternalProcessorAdvice} advice to the list of advices to execute by this internal processor.
     *
     * @param advice the advice to add
     */
    void addAdvice(CamelInternalProcessorAdvice<?> advice);

    /**
     * Gets the advice with the given type.
     *
     * @param  type the type of the advice
     * @return      the advice if exists, or <tt>null</tt> if no advices has been added with the given type.
     */
    <T> T getAdvice(Class<T> type);

    /**
     * Adds advice for handling {@link RoutePolicy} for the route
     */
    void addRoutePolicyAdvice(List<RoutePolicy> routePolicyList);

    /**
     * Adds advice for tracking inflight exchanges for the given route
     */
    void addRouteInflightRepositoryAdvice(InflightRepository inflightRepository, String routeId);

    /**
     * Add advice for setting up {@link UnitOfWork} with the lifecycle of the route.
     */
    void addRouteLifecycleAdvice();

    /**
     * Add advice for JMX management for the route
     */
    void addManagementInterceptStrategy(ManagementInterceptStrategy.InstrumentationProcessor processor);

    /**
     * To make it possible for advices to access the created route.
     */
    void setRouteOnAdvices(Route route);

}
