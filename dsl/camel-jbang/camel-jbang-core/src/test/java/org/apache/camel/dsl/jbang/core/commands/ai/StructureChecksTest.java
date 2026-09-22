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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructureChecksTest {

    @Test
    void onExceptionAfterTheRoutes() {
        // CAMEL-24846: validates today, fails at startup with a RouteBuilder sentence
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - log: "hello"
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant: "true"
                    steps:
                      - log: "handled"
                """;
        List<String> errors = StructureChecks.validateTopLevelOrder(yaml);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isEqualTo("Line 6: onException must come before the routes: move this entry above"
                                            + " the first - route: (line 1); the runtime refuses it at startup"
                                            + " ('onException must be defined before any routes')");
    }

    @Test
    void onExceptionBeforeTheRoutes() {
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    steps:
                      - log: "handled"
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - log: "hello"
                """;
        assertThat(StructureChecks.validateTopLevelOrder(yaml)).isEmpty();
    }

    @Test
    void interceptAndOnCompletionAfterAFromRoute() {
        String yaml = """
                - beans:
                    - name: myBean
                      type: "#class:com.example.MyBean"
                - from:
                    uri: "timer:t?repeatCount=1"
                    steps:
                      - log: "hello"
                - intercept:
                    steps:
                      - log: "intercepted"
                - onCompletion:
                    steps:
                      - log: "done"
                """;
        List<String> errors = StructureChecks.validateTopLevelOrder(yaml);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).startsWith("Line 8: intercept must come before the routes").contains("- from: (line 4)");
        assertThat(errors.get(1)).startsWith("Line 11: onCompletion must come before the routes");
    }

    @Test
    void anOnExceptionInsideARouteIsNotATopLevelEntry() {
        // the schema reports that one ('onException' is a top-level entry: write it as a list item ...)
        String yaml = """
                - route:
                    from:
                      uri: "timer:t?repeatCount=1"
                      steps:
                        - onException:
                            exception:
                              - java.lang.Exception
                """;
        assertThat(StructureChecks.validateTopLevelOrder(yaml)).isEmpty();
    }

    @Test
    void noRoutesNoErrors() {
        assertThat(StructureChecks.validateTopLevelOrder("- onException:\n    steps:\n      - log: x\n")).isEmpty();
        assertThat(StructureChecks.validateTopLevelOrder("")).isEmpty();
    }
}
