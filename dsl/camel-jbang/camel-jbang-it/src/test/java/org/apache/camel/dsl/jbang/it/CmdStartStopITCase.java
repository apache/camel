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

public class CmdStartStopITCase extends JBangTestSupport {

    @Test
    public void testCmdStopByRouteID() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route --id=route1");
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped.*\\n.*route2\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Started");
    }

    @Test
    public void testCmdStopByPID() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        String PID = executeBackground(String.format("run %s/FromDirectoryRoute.java", mountPoint()));
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route " + PID);
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped.*\\n.*route2.*timer:\\/\\/(yaml|java)\\?period=1000\\s+Started");
    }

    @Test
    public void testCmdStopAll() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route");
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped.*\\n.*route2\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped");
    }

    @Test
    public void testCmdStartByRouteID() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route");
        execute("cmd start-route --id=route1");
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Started.*\\n.*route2\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped");
    }

    @Test
    public void testCmdStartByPID() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        String PID = executeBackground(String.format("run %s/FromDirectoryRoute.java", mountPoint()));
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route");
        execute("cmd start-route " + PID);
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Started.*\\n.*route2.*timer:\\/\\/(yaml|java)\\?period=1000\\s+Stopped");
    }

    @Test
    public void testCmdStartAll() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        execute("cmd stop-route");
        execute("cmd start-route");
        checkCommandOutputsPattern("get route",
                "route1\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Started.*\\n.*route2\\s+timer:\\/\\/(yaml|java)\\?period=1000\\s+Started");
    }
}
