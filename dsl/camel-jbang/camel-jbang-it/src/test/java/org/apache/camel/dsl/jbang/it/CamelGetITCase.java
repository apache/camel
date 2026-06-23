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
public class CamelGetITCase extends JBangTestSupport {

    @Test
    public void testGetContext() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        checkCommandOutputs("get context", "Running");
    }

    @Test
    public void testGetRoute() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        checkCommandOutputs("get route", "route1");
        checkCommandOutputs("get route", "route2");
    }

    @Test
    public void testGetGroup() throws IOException {
        copyResourceInDataFolder(TestResources.GROUP_ROUTE);
        executeBackground(String.format("run %s/GroupRoute.java", mountPoint()));
        checkLogContains("Group A message");
        checkCommandOutputs("get group", "groupA");
        checkCommandOutputs("get group", "groupB");
    }

    @Test
    public void testGetMetric() throws IOException {
        copyResourceInDataFolder(TestResources.SERVER_ROUTE);
        executeBackground(String.format("run %s/server.yaml --metrics", mountPoint()));
        checkLogContains("Started route1");
        checkCommandOutputs("get metric", "camel.routes");
    }
}
