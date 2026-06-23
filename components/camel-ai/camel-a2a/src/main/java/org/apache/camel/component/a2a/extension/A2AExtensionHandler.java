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
package org.apache.camel.component.a2a.extension;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.model.AgentExtension;

/**
 * Handler for behavior associated with a negotiated A2A protocol extension.
 *
 * @since 4.21
 */
public interface A2AExtensionHandler {

    /**
     * The extension URI this handler implements.
     *
     * @return the A2A extension URI
     */
    String extensionUri();

    /**
     * Invoked before the route processes a request that negotiated this extension.
     *
     * @param  exchange  the route exchange
     * @param  extension the agent-card extension declaration
     * @throws Exception when extension processing fails
     */
    default void beforeRoute(Exchange exchange, AgentExtension extension) throws Exception {
    }

    /**
     * Invoked after the route processes a request that negotiated this extension.
     *
     * @param  exchange  the route exchange
     * @param  extension the agent-card extension declaration
     * @throws Exception when extension processing fails
     */
    default void afterRoute(Exchange exchange, AgentExtension extension) throws Exception {
    }
}
