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

import java.io.IOException;

import org.apache.camel.util.json.JsonObject;

/**
 * Receives the result of an action executed by {@link CliActionDispatcher}.
 *
 * @since 4.23
 */
public interface CliActionOutput {

    /**
     * The result of the action.
     */
    void write(JsonObject result) throws IOException;

    /**
     * A problem the action reports without failing, such as a route that could not be started. The action has already
     * logged it; transports that can reply to the tool may pass it on.
     */
    default void error(String message) {
        // noop
    }
}
