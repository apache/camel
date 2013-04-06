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

import org.apache.camel.model.ProcessorDefinition;

/**
 * Represents a model of a node in the runtime route path.
 *
 * @version 
 */
public interface RouteNode {

    /**
     * Gets the actual processor this node represents.
     *
     * @return the processor, can be <tt>null</tt> in special cases such as an intercepted node
     */
    Processor getProcessor();

    /**
     * Gets the model definition that represents this node
     *
     * @return the definition, is never <tt>null</tt>
     */
    ProcessorDefinition<?> getProcessorDefinition();

    /**
     * Gets a label about this node to be used for tracing or tooling etc.
     *
     * @param exchange the current exchange
     * @return  a label for this node
     */
    String getLabel(Exchange exchange);

    /**
     * Whether this node is abstract (no real processor under the cover).
     * <p/>
     * Some nodes that represent intermediate steps are abstract, for instance with
     * onException, onCompletion or intercept
     *
     * @return whether this node is abstract or not
     */
    boolean isAbstract();
}
