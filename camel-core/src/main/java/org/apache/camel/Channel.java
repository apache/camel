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

import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Channel acts as a channel between {@link Processor}s in the route graph.
 * <p/>
 * The channel is responsible for routing the {@link Exchange} to the next {@link Processor} in the route graph.
 *
 * @version $Revision$
 */
public interface Channel extends Processor {

    // TODO: This interface method names are not 100% settled yet
    // some methods should many be moved to DefaultChannel only as they are more used for testing purpose
    // and we should add methods to traverse the channels
    // and maybe a channel registry

    /**
     * Sets the processor that the channel should route the {@link Exchange} to.
     *
     * @param output  the next output
     */
    void setNextProcessor(Processor output);

    /**
     * Sets the {@link org.apache.camel.processor.ErrorHandler} that the Channel uses.

     * @param errorHandler the error handler
     */
    void setErrorHandler(Processor errorHandler);

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
     * Initializes the channel.
     *
     * @param outputDefinition  the route defintion the {@link Channel} represents
     * @param routeContext      the route context
     * @throws Exception is thrown if some error occured
     */
    void initChannel(ProcessorDefinition outputDefinition, RouteContext routeContext) throws Exception;

    /**
     * Gets the wrapped output that at runtime should be delegated to.
     *
     * @return the output delegated to
     */
    Processor getOutput();

    /**
     * Gets the original next {@link Processor} that is not wrapped.
     *
     * @return  the next processor
     */
    Processor getNextProcessor();

    boolean hasInterceptorStrategy(Class type);

}
