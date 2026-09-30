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
package org.apache.camel.cli.connector;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;

/**
 * Moves actions from a tool to the {@link CliActionDispatcher}, and results and snapshots back to the tool.
 * <p/>
 * The default transport exchanges files in <tt>~/.camel</tt> with the Camel CLI. The transport is chosen with the
 * <tt>camel.cli.transport</tt> property.
 *
 * @since 4.23
 */
public interface CliConnectorTransport extends Service {

    /**
     * Called once before the transport is started.
     *
     * @param camelContext the context
     * @param dispatcher   executes actions (not thread-safe)
     * @param snapshots    collects snapshots
     * @param shutdown     shuts down the integration, when the tool asks for it
     */
    void configure(
            CamelContext camelContext, CliActionDispatcher dispatcher, CliSnapshotProducer snapshots, Runnable shutdown);

    /**
     * Changes how often the transport polls for actions and updates snapshots, in millis.
     */
    default void updateDelay(int delay) {
        // noop
    }
}
