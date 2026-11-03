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
public class RunMaxSecondsITCase extends JBangTestSupport {

    @Test
    public void testRunWithMaxSeconds() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // run blocks until the integration stops; --max-seconds causes it to stop after N seconds
        execute(String.format("run %s/route2.yaml --max-seconds=5", mountPoint()));
        // after the command returns no integration is running
        Assertions.assertThat(execute("ps")).as("no process should be running after --max-seconds").isEmpty();
    }

    @Test
    public void testRunWithMaxMessages() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // integration stops after processing the given number of messages
        execute(String.format("run %s/route2.yaml --max-messages=3", mountPoint()));
        Assertions.assertThat(execute("ps")).as("no process should be running after --max-messages").isEmpty();
    }
}
