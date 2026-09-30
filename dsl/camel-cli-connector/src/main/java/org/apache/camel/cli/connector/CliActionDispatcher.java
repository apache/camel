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
 * Executes the actions sent by a tool, such as the Camel CLI.
 * <p/>
 * An action is the JSON document the Camel CLI writes to the action file, for example
 * <tt>{"action":"route","command":"stop","id":"route1"}</tt>.
 * <p/>
 * Implementations are not thread-safe: a transport must call {@link #dispatch(JsonObject, CliActionOutput)} from one
 * thread at a time.
 *
 * @since 4.23
 */
public interface CliActionDispatcher {

    /**
     * Executes the action.
     *
     * @param  action    the action
     * @param  output    receives the result of the action (not every action has a result)
     * @return           <tt>false</tt> if the action is unknown
     * @throws Exception if the action failed
     */
    boolean dispatch(JsonObject action, CliActionOutput output) throws Exception;
}
