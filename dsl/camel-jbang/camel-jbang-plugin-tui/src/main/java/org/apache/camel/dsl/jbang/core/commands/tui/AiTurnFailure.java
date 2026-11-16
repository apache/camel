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
package org.apache.camel.dsl.jbang.core.commands.tui;

/**
 * The text the AI panel shows, and writes to the AI log, for what ended a turn: of the built-in model or of an ACP
 * agent, also an {@link Error} and a failure with no message. A plugin jar of the CLI built after the core jar it calls
 * ended each answer in a {@link NoSuchMethodError}: the turn was over with no answer, no error and nothing in the AI
 * log.
 */
final class AiTurnFailure {

    private AiTurnFailure() {
    }

    /**
     * The message of the failure, or its class name when it has none. A {@link LinkageError} says that the jars of the
     * Camel CLI do not match, as that is what it means here.
     */
    static String describe(Throwable e) {
        if (e instanceof LinkageError) {
            return "The jars of the Camel CLI do not match (built at different times?): " + e;
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
