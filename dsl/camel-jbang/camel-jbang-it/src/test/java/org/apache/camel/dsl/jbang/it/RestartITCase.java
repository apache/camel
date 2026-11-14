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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Tag("container-only")
@DisabledOnOs(WINDOWS)
public class RestartITCase extends JBangTestSupport {

    @Test
    public void testRestart() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        String pid = executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute("restart " + pid);
        // after restart a new process starts; verify the old PID is gone and integration is Running again
        Awaitility.await()
                .atMost(Duration.ofSeconds(ASSERTION_WAIT_SECONDS))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    String ps = execute("ps");
                    Assertions.assertThat(ps).as("restarted integration should be Running").contains("Running");
                    Assertions.assertThat(ps).as("old PID should not be present after restart")
                            .doesNotContainPattern("(?m)(^|\\s)" + pid + "(\\s|$)");
                });
        checkCommandOutputs("get context", "Running");
    }
}
