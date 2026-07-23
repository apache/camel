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

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Supports forward traversal of a directed graph where each step returns a {@link List} of successor nodes of type
 * {@code T} (0 to n nodes per step).
 * <p/>
 * In the Camel route model the graph is the EIP pipeline tree rooted at each {@link Route}. Every model node that can
 * have children implements {@code Navigate} so that tools such as the backlog tracer, route visualizer, and the
 * debugger can walk the entire processing graph without coupling to a specific model class hierarchy.
 * <p/>
 * Callers must invoke {@link #next()} and {@link #hasNext()} <em>at most once each</em>: the interface is stateless and
 * not designed for repeated iteration.
 *
 * @param <T> the node type returned by each traversal step
 * @see       NamedNode
 * @see       Route
 */
public interface Navigate<T> {

    /**
     * Next group of outputs
     * <p/>
     * Important only invoke this once, as this method do not carry state, and is not intended to be used in a while
     * loop, but used by a if statement instead.
     *
     * @return next group or <tt>null</tt> if no more outputs
     */
    @Nullable
    List<T> next();

    /**
     * Are there more outputs?
     * <p/>
     * Important only invoke this once, as this method do not carry state, and is not intended to be used in a while
     * loop, but used by a if statement instead.
     *
     * @return <tt>true</tt> if more outputs
     */
    boolean hasNext();

}
