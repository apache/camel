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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListAboutTest extends ProcessCommandTestSupport {

    @TempDir
    Path project;

    @Test
    void whatTheIntegrationDoesAndWhatHasHappened() throws Exception {
        String out = run(command(timeline()), false);
        assertThat(out).contains("myApp (pid " + TEST_PID + ")")
                .contains("Every order is checked against the stock.")
                .contains("In").contains("timer:orders")
                .contains("Routes").contains("orders  timer → 1 step").contains("  ↳ stock  direct → 1 step")
                .contains("Last 3s").contains("6 exchanges");
    }

    @Test
    void asJson() throws Exception {
        JsonObject about = (JsonObject) Jsoner.deserialize(run(command(null), true));
        assertThat(about.getString("name")).isEqualTo("myApp");
        assertThat(about.getString("purpose")).isEqualTo("Every order is checked against the stock.");
        assertThat((List<?>) about.get("routes")).hasSize(2);
        // not recording a timeline: no last minutes
        assertThat(about.containsKey("lastMinutes")).isFalse();
    }

    private String run(ListAbout command, boolean json) throws Exception {
        Files.writeString(project.resolve("shop.camel.yaml"), """
                # Every order is checked against the stock.
                - route:
                    id: orders
                    from:
                      uri: timer:orders
                      steps:
                        - to: direct:stock
                - route:
                    id: stock
                    from:
                      uri: direct:stock
                      steps:
                        - log: checked
                """);
        JsonObject status = buildContextStatus("myApp", 5);
        JsonObject runtime = new JsonObject();
        runtime.put("directory", project.toString());
        status.put("runtime", runtime);
        writeStatusFile(TEST_PID, status);
        command.jsonOutput = json;
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            assertThat(command.doCall()).isZero();
            return printer.getOutput().trim();
        }
    }

    private ListAbout command(JsonObject timeline) {
        return new ListAbout(new CamelJBangMain().withPrinter(printer)) {
            @Override
            JsonObject loadTimeline(long pid) {
                return timeline;
            }
        };
    }

    private static JsonObject timeline() throws Exception {
        return (JsonObject) Jsoner.deserialize("""
                {"end": 1800000000, "seconds": 3, "exchangesDone": [2, 2, 2],
                 "routes": [{"routeId": "orders", "completed": [2, 2, 2], "recovered": [0, 0, 0],
                             "handled": [0, 0, 0], "failed": [0, 0, 0]}]}
                """);
    }
}
