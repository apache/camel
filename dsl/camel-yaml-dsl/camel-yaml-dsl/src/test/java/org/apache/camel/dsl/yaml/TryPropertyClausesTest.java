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

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * doCatch and doFinally written as properties of doTry are kept in its outputs, after the steps whatever the YAML key
 * order (CAMEL-25410).
 */
class TryPropertyClausesTest extends YamlTestSupport {

    private static final String CLAUSES_ABOVE_STEPS = """
            - route:
                id: clauses
                from:
                  uri: direct:start
                  steps:
                    - doTry:
                        doCatch:
                          - exception:
                              - java.lang.IllegalStateException
                            steps:
                              - to: mock:catch
                        doFinally:
                          steps:
                            - to: mock:finally
                        steps:
                          - to: mock:try
                          - throwException:
                              exceptionType: java.lang.IllegalStateException
                              message: Forced
            """;

    @Test
    void clausesAboveStepsAreAfterTheStepsInTheOutputs() throws Exception {
        loadRoutes(CLAUSES_ABOVE_STEPS);

        TryDefinition doTry = (TryDefinition) context.getRouteDefinition("clauses").getOutputs().get(0);
        assertThat(doTry.getOutputs()).hasSize(4);
        assertThat(doTry.getOutputs().get(0)).isInstanceOf(ToDefinition.class);
        assertThat(doTry.getOutputs().get(2)).isInstanceOf(CatchDefinition.class);
        assertThat(doTry.getOutputs().get(3)).isInstanceOf(FinallyDefinition.class);
        assertThat(doTry.getCatchClauses()).containsExactly((CatchDefinition) doTry.getOutputs().get(2));
        assertThat(doTry.getFinallyClause()).isSameAs(doTry.getOutputs().get(3));

        assertRuns();
    }

    @Test
    void clausesAboveStepsSurviveDumpAndReload() throws Exception {
        loadRoutes(CLAUSES_ABOVE_STEPS);
        var route = context.getRouteDefinition("clauses");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        assertThat(yaml).contains("doCatch:", "doFinally:");
        assertThat(yaml.indexOf("mock:try")).isLessThan(yaml.indexOf("mock:catch"));
        assertThat(yaml.indexOf("mock:catch")).isLessThan(yaml.indexOf("mock:finally"));

        context.removeRouteDefinition(route);
        loadRoutes(yaml);

        assertRuns();
    }

    @Test
    void templatedRoutesRunEachClauseOnce() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: myTemplate
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - doTry:
                            steps:
                              - throwException:
                                  exceptionType: java.lang.IllegalStateException
                                  message: Forced
                            doCatch:
                              - exception:
                                  - java.lang.IllegalStateException
                                steps:
                                  - to: "mock:catch-{{name}}"
                            doFinally:
                              steps:
                                - to: "mock:finally-{{name}}"
                - templatedRoute:
                    routeId: one
                    routeTemplateRef: myTemplate
                    parameters:
                      - name: name
                        value: one
                - templatedRoute:
                    routeId: two
                    routeTemplateRef: myTemplate
                    parameters:
                      - name: name
                        value: two
                """);

        for (String name : new String[] { "one", "two" }) {
            withMock("mock:catch-" + name, mock -> mock.expectedMessageCount(1));
            withMock("mock:finally-" + name, mock -> mock.expectedMessageCount(1));
        }
        context.start();

        withTemplate(t -> t.to("direct:one").withBody("Hello").send());
        withTemplate(t -> t.to("direct:two").withBody("World").send());

        MockEndpoint.assertIsSatisfied(context);
        TryDefinition doTry = (TryDefinition) context.getRouteDefinition("one").getOutputs().get(0);
        assertThat(doTry.getCatchClauses()).hasSize(1);
    }

    private void assertRuns() throws Exception {
        withMock("mock:try", mock -> mock.expectedMessageCount(1));
        withMock("mock:catch", mock -> mock.expectedMessageCount(1));
        withMock("mock:finally", mock -> mock.expectedMessageCount(1));
        context.start();
        withTemplate(t -> t.to("direct:start").withBody("Hello").send());
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }
}
