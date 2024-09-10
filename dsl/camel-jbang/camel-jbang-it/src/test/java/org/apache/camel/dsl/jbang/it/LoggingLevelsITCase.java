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

public class LoggingLevelsITCase extends JBangTestSupport {

    @Test
    public void testLoggingLevelRuntime() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        executeBackground(String.format("run %s/FromDirectoryRoute.java", mountPoint()));
        checkLogContains("Hello world!");
        checkLogDoesNotContain("Invoke health-check (READINESS) camel/context");
        execute("cmd logger --logging-level=DEBUG FromDirectoryRoute");
        checkLogContains("Invoke health-check (READINESS) camel/context");
    }

    @Test
    public void testSetLoggingLevel() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        executeBackground(String.format("run %s/FromDirectoryRoute.java --logging-level=DEBUG", mountPoint()));
        checkLogContains("Hello world!");
        checkLogContains("Invoke health-check (READINESS) camel/context");
    }
}
