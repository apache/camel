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
 * A link on a line to where it leads; callersOf is the endpoint of a route sent to from more than one place, whose link
 * opens a popup to choose one (CAMEL-25411), else null. how is how the send reaches the route: call when the sending
 * route waits for the answer, async when it hands the message off, null when not a send between routes.
 */
record JumpLink(String routeId, String filePath, int targetLine, String callersOf, String how) {

    JumpLink(String routeId, String filePath, int targetLine) {
        this(routeId, filePath, targetLine, null, null);
    }

    JumpLink(String routeId, String filePath, int targetLine, String callersOf) {
        this(routeId, filePath, targetLine, callersOf, null);
    }

    JumpLink withHow(String how) {
        return new JumpLink(routeId, filePath, targetLine, callersOf, how);
    }

    /** The hint after the line: ↳ the route waits for the answer, ⇢ a hand-off, as the About view marks them. */
    String hint() {
        String mark = "call".equals(how) ? "↳ " : "async".equals(how) ? "⇢ " : "";
        return " ↵ " + mark + routeId;
    }
}
