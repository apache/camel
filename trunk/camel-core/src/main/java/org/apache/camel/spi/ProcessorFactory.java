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

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

/**
 * A factory to create {@link Processor} based on the {@link org.apache.camel.model.ProcessorDefinition definition}.
 * <p/>
 * This allows you to implement a custom factory in which you can control the creation of the processors.
 * It also allows you to manipulate the {@link org.apache.camel.model.ProcessorDefinition definition}s for example to
 * configure or change options. Its also possible to add new steps in the route by adding outputs to
 * {@link org.apache.camel.model.ProcessorDefinition definition}s.
 * <p/>
 * <b>Important:</b> By returning <tt>null</tt> from the create methods you fallback to let the default implementation in Camel create
 * the {@link Processor}. You want to do this if you <i>only</i> want to manipulate the
 * {@link org.apache.camel.model.ProcessorDefinition definition}s.
 *
 * @version 
 */
public interface ProcessorFactory {

    /**
     * Creates the child processor.
     * <p/>
     * The child processor is an output from the given definition, for example the sub route in a splitter EIP.
     *
     * @param routeContext  the route context
     * @param definition    the definition which represents the processor
     * @param mandatory     whether or not the child is mandatory
     * @return the created processor, or <tt>null</tt> to let the default implementation in Camel create the processor.
     * @throws Exception can be thrown if error creating the processor
     */
    Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception;

    /**
     * Creates the processor.
     *
     * @param routeContext  the route context
     * @param definition    the definition which represents the processor
     * @return the created processor, or <tt>null</tt> to let the default implementation in Camel create the processor.
     * @throws Exception can be thrown if error creating the processor
     */
    Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception;

}
