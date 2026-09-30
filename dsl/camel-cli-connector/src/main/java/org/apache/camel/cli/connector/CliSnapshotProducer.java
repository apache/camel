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

import org.apache.camel.util.json.JsonObject;

/**
 * Collects the snapshots a transport periodically sends to the tool.
 * <p/>
 * Each method returns <tt>null</tt> when the snapshot is not available (the dev console is not on the classpath). The
 * trace and receive snapshots return every message still held by Camel; a transport that sends them incrementally keeps
 * track of the last <tt>uid</tt> it has sent.
 *
 * @since 4.23
 */
public interface CliSnapshotProducer {

    /**
     * Status of the integration: runtime, context, routes, health, memory, and more.
     */
    JsonObject status() throws Exception;

    /**
     * Traced messages (<tt>traces</tt> array, each with a <tt>uid</tt>).
     */
    JsonObject trace() throws Exception;

    /**
     * Debugger state.
     */
    JsonObject debug() throws Exception;

    /**
     * Message history of the last completed exchange.
     */
    JsonObject messageHistory() throws Exception;

    /**
     * Recent routing errors.
     */
    JsonObject errors() throws Exception;

    /**
     * Received messages (<tt>messages</tt> array, each with a <tt>uid</tt>).
     */
    JsonObject receive() throws Exception;

    /**
     * Route activity.
     */
    JsonObject activity() throws Exception;
}
