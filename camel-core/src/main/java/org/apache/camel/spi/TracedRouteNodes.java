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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RouteNode;
import org.apache.camel.model.ProcessorDefinition;

/**
 * Tracing information used by {@link org.apache.camel.processor.interceptor.TraceInterceptor}
 * so we can trace the exact route path a given {@link org.apache.camel.Exchange} has been processed.
 *
 * @deprecated use {@link Exchange#MESSAGE_HISTORY} instead.
 */
@Deprecated
public interface TracedRouteNodes {

    /**
     * Adds the entry that was intercepted
     *
     * @param entry the entry
     */
    void addTraced(RouteNode entry);

    /**
     * Gets the last node, is <tt>null</tt> if no last exists.
     *
     * @return the last node
     */
    RouteNode getLastNode();

    /**
     * Gets the 2nd last node, is <tt>null</tt> if no last exists.
     *
     * @return the 2nd last
     */
    RouteNode getSecondLastNode();

    /**
     * Gets the current list of nodes, representing the route path the
     * current {@link org.apache.camel.Exchange} has currently taken.
     *
     * @return the node path
     */
    List<RouteNode> getNodes();

    /**
     * Prepares a new block for tracing.
     * <p/>
     * This is needed when you have child block such as a multicast or aggregator
     */
    void pushBlock();

    /**
     * Pops the last block from tracing.
     */
    void popBlock();

    /**
     * Clears all traced information
     */
    void clear();

    /**
     * A private counter that increments, is used to as book keeping how far this
     * exchange have been intercepted by the general intercept().
     * <p/>
     * We need this special book keeping to keep correct order when dealing
     * with concurrent exchanges being routed in the same route path.
     *
     * @param node the intercept node
     * @return the current count
     */
    int getAndIncrementCounter(ProcessorDefinition<?> node);

}
