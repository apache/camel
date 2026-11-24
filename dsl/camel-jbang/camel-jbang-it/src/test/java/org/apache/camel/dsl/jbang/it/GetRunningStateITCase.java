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
        // pass a known property on the command line so get properties has something stable to verify
        executeBackground(String.format("run %s/route2.yaml --property=test.jbang.marker=running", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        checkCommandOutputs("get properties", "test.jbang.marker", ASSERTION_WAIT_SECONDS);
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
    public void testGetInflight() {
        // the 5-second delay keeps exchanges in-flight long enough to observe them
        executeBackground("run --code='from(\"timer:t?period=500\").delay(5000).log(\"inflight\")'");
        // the EXCHANGE-ID header only appears when there is at least one in-flight exchange
        checkCommandOutputs("get inflight", "EXCHANGE-ID", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testGetVariable() {
        // global: prefix stores the variable in the CamelContext scope, visible via get variable
        executeBackground(
                "run --code='from(\"timer:t?period=500\").setVariable(\"global:myKey\", constant(\"myValue\")).log(\"${variable:global:myKey}\")'");
        checkLogContains("myValue");
        checkCommandOutputs("get variable", "myKey", ASSERTION_WAIT_SECONDS);
    }
}
