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

import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.spi.InterceptStrategy;

/**
 * Channel acts as a channel between {@link Processor}s in the route graph.
 * <p/>
 * The channel is responsible for routing the {@link Exchange} to the next {@link Processor} in the route graph.
 */
public interface Channel extends AsyncProcessor, Navigate<Processor> {

    /**
     * Initializes the channel. If the initialized output definition contained outputs (children) then the
     * childDefinition will be set so we can leverage fine grained tracing
     */
    void initChannel(
            Route route,
            NamedNode definition,
            NamedNode childDefinition,
            List<InterceptStrategy> interceptors,
            Processor nextProcessor,
            NamedRoute routeDefinition,
            boolean first)
            throws Exception;

    /**
     * Post initializes the channel.
     *
     * @throws Exception is thrown if some error occurred
     */
    void postInitChannel() throws Exception;

    /**
     * Gets the {@link ErrorHandler} this Channel uses.
     *
     * @return the error handler, or <tt>null</tt> if no error handler is used.
     */
    Processor getErrorHandler();

    /**
     * Sets the {@link ErrorHandler} this Channel uses.
     *
     * @param errorHandler the error handler
     */
    void setErrorHandler(Processor errorHandler);

    /**
     * Gets the wrapped output that at runtime should be delegated to.
     *
     * @return the output to route the {@link Exchange} to
     */
    Processor getOutput();

    /**
     * Gets the next {@link Processor} to route to (not wrapped)
     *
     * @return the next processor
     */
    Processor getNextProcessor();

    /**
     * Gets the {@link Route}
     *
     * @return the route context
     */
    Route getRoute();

}
