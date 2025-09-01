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

package org.apache.camel.component.langchain4j.agent.api;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;

/**
 * Factory interface for creating AI agent instances within the Apache Camel LangChain4j integration.
 *
 * <p>
 * This factory provides a standardized way to create and manage AI agents, supporting both agents with memory
 * capabilities and stateless agents. Implementations of this interface are responsible for configuring the underlying
 * LangChain4j AI services with appropriate models, memory providers, and other necessary components.
 * </p>
 *
 * <p>
 * The factory extends {@link CamelContextAware} to ensure proper integration with the Camel context and access to
 * registry components.
 * </p>
 *
 * @since 4.9.0
 */
public interface AgentFactory extends CamelContextAware {

    /**
     * Creates a new AI agent instance configured with the appropriate settings.
     *
     * <p>
     * Implementations may choose to cache agent instances for performance optimization, especially when the underlying
     * configuration remains unchanged. The returned agent will be fully configured and ready to handle chat
     * interactions.
     * </p>
     *
     * @return           a configured {@link Agent} instance ready for chat interactions
     * @throws Exception if unable to create the agent due to configuration issues, missing dependencies, or
     *                   initialization failures
     */
    @Deprecated
    default Agent createAgent() throws Exception {
        return createAgent(null);
    }

    /**
     * Creates a new AI agent instance configured with the appropriate settings.
     *
     * <p>
     * Implementations may choose to cache agent instances for performance optimization, especially when the underlying
     * configuration remains unchanged. The returned agent will be fully configured and ready to handle chat
     * interactions.
     * </p>
     *
     * @param  exchange  the exchange being processed which is triggering the creation of the agent
     *
     * @return           a configured {@link Agent} instance ready for chat interactions
     * @throws Exception if unable to create the agent due to configuration issues, missing dependencies, or
     *                   initialization failures
     */
    Agent createAgent(Exchange exchange) throws Exception;
}
