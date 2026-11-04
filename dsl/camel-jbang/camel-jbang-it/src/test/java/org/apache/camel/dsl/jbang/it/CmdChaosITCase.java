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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class CmdChaosITCase extends JBangTestSupport {

    // until must not exceed camel.chaos.maxDuration default (10m)
    private static final String RULE = "endpoint=timer:yaml delay duration=500ms rate=1.0 until=5m";

    @Test
    public void testCmdChaosAddAndList() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        String pid = executeBackground(String.format("run %s/route2.yaml --chaos", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute(String.format("cmd chaos-add %s --rule='%s'", pid, RULE));
        checkCommandOutputs("get chaos", "timer", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testCmdChaosClear() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        String pid = executeBackground(String.format("run %s/route2.yaml --chaos", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute(String.format("cmd chaos-add %s --rule='%s'", pid, RULE));
        checkCommandOutputs("get chaos", "timer", ASSERTION_WAIT_SECONDS);
        execute(String.format("cmd chaos-clear %s", pid));
        checkCommandDoesNotOutput("get chaos", "timer");
    }

    @Test
    public void testCmdChaosChangeAndRemove() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        String pid = executeBackground(String.format("run %s/route2.yaml --chaos", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        String addOutput = execute(String.format("cmd chaos-add %s --rule='%s'", pid, RULE));
        // chaos-add outputs "Added chaos rule r1" — extract the rule id
        Assertions.assertThat(addOutput).containsPattern("Added chaos rule r\\d+");
        String ruleId = addOutput.replaceAll(".*Added chaos rule (r\\d+).*", "$1").trim();
        execute(String.format("cmd chaos-change %s --id=%s --rule='%s'", pid, ruleId,
                "endpoint=timer:yaml delay duration=1s rate=0.5 until=5m"));
        checkCommandOutputs("get chaos", "timer", ASSERTION_WAIT_SECONDS);
        execute(String.format("cmd chaos-remove %s --id=%s", pid, ruleId));
        checkCommandDoesNotOutput("get chaos", "timer");
    }
}
