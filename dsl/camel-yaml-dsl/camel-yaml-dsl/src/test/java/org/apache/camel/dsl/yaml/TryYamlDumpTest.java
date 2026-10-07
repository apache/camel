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
package org.apache.camel.dsl.yaml;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TryYamlDumpTest extends YamlTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void propertyClausesSurviveDump(boolean started) throws Exception {
        loadRoutes("""
                - route:
                    id: original
                    from:
                      uri: direct:start
                      steps:
                        - doTry:
                            steps:
                              - to: mock:try
                            doCatch:
                              - exception:
                                  - java.lang.IllegalStateException
                                steps:
                                  - to: mock:catch
                            doFinally:
                              steps:
                                - to: mock:finally
                """);
        var route = context.getRouteDefinition("original");
        if (started) {
            context.start();
        }
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        assertThat(yaml).contains("doCatch:", "doFinally:", "mock:catch", "mock:finally");
        if (started) {
            context.stop();
        }
        context.removeRouteDefinition(route);
        loadRoutes(yaml);
        TryDefinition restored = (TryDefinition) context.getRouteDefinition("original").getOutputs().get(0);
        assertThat(restored.getCatchClauses()).hasSize(1);
        assertThat(restored.getCatchClauses().get(0).getExceptions()).containsExactly("java.lang.IllegalStateException");
        assertThat(restored.getFinallyClause()).isNotNull();
    }
}
