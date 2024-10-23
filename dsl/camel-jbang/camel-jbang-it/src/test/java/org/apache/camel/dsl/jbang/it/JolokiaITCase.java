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
import java.time.Duration;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class JolokiaITCase extends JBangTestSupport {

    @Test
    public void testAttachJolokia() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        String processID = executeBackground(String.format("run %s/FromDirectoryRoute.java", mountPoint()));
        checkLogContains("(FromDirectoryRoute) started");
        execute("jolokia FromDirectoryRoute");
        Assertions.assertThat(execInContainer("curl http://127.0.0.1:8778/jolokia/"))
                .as("Jolokia should be reachable")
                .contains("\"agentContext\":\"/jolokia\"");
        Assertions.assertThat(execute("jolokia FromDirectoryRoute --stop"))
                .as("Jolokia should stop")
                .contains("Stopped Jolokia for PID " + processID);

    }

    @Test
    public void testRunHawtio() throws IOException, InterruptedException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        executeBackground(String.format("run %s/FromDirectoryRoute.java", mountPoint()));
        checkLogContains("(FromDirectoryRoute) started");
        execInContainer("nohup camel hawtio FromDirectoryRoute &");
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions
                        .assertThat(
                                execInContainer("curl http://localhost:8888/hawtio/"))
                        .as("Hawtio should be reachable")
                        .contains("content=\"Hawtio Management Console\""));
        Assertions.assertThat(execInContainer("curl http://127.0.0.1:8778/jolokia/"))
                .as("Jolokia agent should be attached")
                .contains("\"agentContext\":\"/jolokia\"");
    }
}
