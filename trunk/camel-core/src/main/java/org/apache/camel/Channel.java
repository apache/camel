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
package org.apache.camel;

import java.util.List;

import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Channel acts as a channel between {@link Processor}s in the route graph.
 * <p/>
 * The channel is responsible for routing the {@link Exchange} to the next {@link Processor} in the route graph.
 *
 * @version 
 */
public interface Channel extends AsyncProcessor, Navigate<Processor> {

    /**
     * Sets the processor that the channel should route the {@link Exchange} to.
     *
     * @param next  the next processor
     */
    void setNextProcessor(Processor next);

    /**
     * Sets the {@link org.apache.camel.processor.ErrorHandler} this Channel uses.
     *
     * @param errorHandler the error handler
     */
    void setErrorHandler(Processor errorHandler);

    /**
     * Gets the {@link org.apache.camel.processor.ErrorHandler} this Channel uses.
     *
     * @return the error handler, or <tt>null</tt> if no error handler is used.
     */
    Processor getErrorHandler();

    /**
     * Adds a {@link org.apache.camel.spi.InterceptStrategy} to apply each {@link Exchange} before
     * its routed to the next {@link Processor}.
     *
     * @param strategy  the intercept strategy
     */
    void addInterceptStrategy(InterceptStrategy strategy);

    /**
     * Adds a list of {@link org.apache.camel.spi.InterceptStrategy} to apply each {@link Exchange} before
     * its routed to the next {@link Processor}.
     *
     * @param strategy  list of strategies
     */
    void addInterceptStrategies(List<InterceptStrategy> strategy);

    /**
     * Gets the list of {@link org.apache.camel.spi.InterceptStrategy} registered to this Channel.
     *
     * @return list of strategies, returns an empty list if no strategies is registered.
     */
    List<InterceptStrategy> getInterceptStrategies();

    /**
     * Gets the wrapped output that at runtime should be delegated to.
     *
     * @return the output to route the {@link Exchange} to
     */
    Processor getOutput();

    /**
     * Sets the wrapped output that at runtime should be delegated to.
     *
     * @param output the output to route the {@link Exchange} to
     */
    void setOutput(Processor output);

    /**
     * Gets the next {@link Processor} to route to (not wrapped)
     *
     * @return  the next processor
     */
    Processor getNextProcessor();

    /**
     * Gets the {@link RouteContext}
     *
     * @return the route context
     */
    RouteContext getRouteContext();
}
