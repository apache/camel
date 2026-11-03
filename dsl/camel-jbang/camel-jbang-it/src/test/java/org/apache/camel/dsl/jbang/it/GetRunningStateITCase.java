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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class GetRunningStateITCase extends JBangTestSupport {

    @Test
    public void testGetEvent() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // route lifecycle events (e.g. RouteStarted) are stored in memory
        checkCommandOutputs("get event", "route1", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testGetSource() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        checkCommandOutputs("get source", "route2.yaml");
    }

    @Test
    public void testGetProperties() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // properties command runs without error; result may be empty if no application.properties is present
        execute("get properties");
    }

    @Test
    public void testGetActivity() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // activity shows recently processed exchanges; route1 is the timer route
        checkCommandOutputs("get activity", "route1", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testGetCount() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // count shows exchange totals per integration; TOTAL column appears once exchanges have been processed
        checkCommandOutputs("get count", "TOTAL", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testGetRouteController() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        checkCommandOutputs("get route-controller", "route1", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testGetInflight() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // inflight exchanges may be empty for a fast timer route; just verify the command does not crash
        execute("get inflight");
    }

    @Test
    public void testGetVariable() {
        // exchange variables are ephemeral; get variable shows context-level variables
        // so here we just verify the command runs without error
        executeBackground(
                "run --code='from(\"timer:foo?period=500\").log(\"hello\")'");
        checkLogContains("hello");
        execute("get variable");
    }
}
