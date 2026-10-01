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

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDecisions.DecisionPoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverviewTest.CATALOG;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25161: the decision points of a route and their paths, alike in every DSL and as the running route tree nests
 * them (choice/when/filter, doTry/doCatch, split, loop, aggregate).
 */
class RouteDecisionsTest {

    /** The paths the running route tree of this route gives, see RouteStructureDevConsole. */
    private static final List<String> PATHS = List.of(
            "choice[1]", "choice[1]/when[1]", "choice[1]/when[1]/filter[1]", "choice[1]/when[2]",
            "choice[1]/otherwise[1]", "doTry[1]", "doTry[1]/doCatch[1]", "split[1]", "loop[1]");

    @Test
    void java() {
        Route r = route("Orders.java", """
                public class Orders extends RouteBuilder {
                    public void configure() {
                        from("direct:a").routeId("a")
                            .choice()
                                .when(simple("${header.x} > 5")).to("mock:big")
                                    .filter(simple("${body} != null")).to("mock:f").endChoice()
                                .when(simple("${header.y}")).to("mock:j")
                                .otherwise().to("mock:o")
                            .end()
                            .doTry().to("mock:t")
                            .doCatch(IllegalArgumentException.class).to("mock:c")
                            .end()
                            .doSwitch(header("department")).doCase("a", "direct:x").otherwise("direct:y").end()
                            .split(body().tokenize(",")).to("mock:s").end()
                            .loop(3).to("mock:l").end();
                    }
                }
                """);
        assertThat(r.decisions()).extracting(DecisionPoint::path).containsExactlyElementsOf(PATHS);
        assertThat(r.decisions().get(1).expression()).isEqualTo("simple: ${header.x} > 5");
        assertThat(r.decisions().get(1).line()).isEqualTo(5);
        assertThat(r.decisions().get(6).expression()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(r.decisions().get(0).expression()).isNull();
    }

    @Test
    void yaml() {
        Route r = route("orders.camel.yaml", """
                - route:
                    id: a
                    from:
                      uri: direct:a
                      steps:
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${header.x} > 5"
                                steps:
                                  - to:
                                      uri: mock:big
                                  - filter:
                                      simple: "${body} != null"
                                      steps:
                                        - to:
                                            uri: mock:f
                              - simple: "${header.y}"
                                steps:
                                  - to:
                                      uri: mock:j
                            otherwise:
                              steps:
                                - to:
                                    uri: mock:o
                        - doTry:
                            steps:
                              - to:
                                  uri: mock:t
                            doCatch:
                              - exception:
                                  - java.lang.IllegalArgumentException
                                steps:
                                  - to:
                                      uri: mock:c
                        - switch:
                            selector:
                              header:
                                expression: department
                            case:
                              - value: a
                                uri: direct:x
                            otherwise:
                              uri: direct:y
                        - split:
                            tokenize:
                              token: ","
                            steps:
                              - to:
                                  uri: mock:s
                        - loop:
                            constant: "3"
                            steps:
                              - to:
                                  uri: mock:l
                """);
        assertThat(r.decisions()).extracting(DecisionPoint::path).containsExactlyElementsOf(PATHS);
        assertThat(r.decisions().get(1).expression()).isEqualTo("simple: ${header.x} > 5");
        assertThat(r.decisions().get(2).expression()).isEqualTo("simple: ${body} != null");
        assertThat(r.decisions().get(6).expression()).isEqualTo("java.lang.IllegalArgumentException");
    }

    @Test
    void xml() {
        Route r = route("orders.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route id="a">
                    <from uri="direct:a"/>
                    <choice>
                      <when>
                        <simple>${header.x} &gt; 5</simple>
                        <to uri="mock:big"/>
                        <filter>
                          <simple>${body} != null</simple>
                          <to uri="mock:f"/>
                        </filter>
                      </when>
                      <when>
                        <simple>${header.y}</simple>
                        <to uri="mock:j"/>
                      </when>
                      <otherwise>
                        <to uri="mock:o"/>
                      </otherwise>
                    </choice>
                    <doTry>
                      <to uri="mock:t"/>
                      <doCatch>
                        <exception>java.lang.IllegalArgumentException</exception>
                        <to uri="mock:c"/>
                      </doCatch>
                    </doTry>
                    <switch>
                      <selector><header>department</header></selector>
                      <case value="a" uri="direct:x"/>
                      <otherwise uri="direct:y"/>
                    </switch>
                    <split>
                      <tokenize token=","/>
                      <to uri="mock:s"/>
                    </split>
                    <loop>
                      <constant>3</constant>
                      <to uri="mock:l"/>
                    </loop>
                  </route>
                </routes>
                """);
        assertThat(r.decisions()).extracting(DecisionPoint::path).containsExactlyElementsOf(PATHS);
        assertThat(r.decisions().get(1).expression()).isEqualTo("simple: ${header.x} > 5");
        assertThat(r.decisions().get(6).expression()).isEqualTo("java.lang.IllegalArgumentException");
    }

    @Test
    void atMostTenPerRoute() {
        StringBuilder sb = new StringBuilder("from(\"direct:a\")");
        for (int i = 0; i < 15; i++) {
            sb.append(".filter(simple(\"${header.n} == ").append(i).append("\")).to(\"mock:x\").end()");
        }
        Route r = route("Many.java", "public class Many extends RouteBuilder { public void configure() { " + sb + "; } }");
        assertThat(r.decisions()).hasSize(RouteDecisions.MAX_PER_ROUTE);
        assertThat(r.decisions().get(9).path()).isEqualTo("filter[10]");
    }

    private static Route route(String file, String content) {
        List<Route> routes = ProjectRoutes.parse(file, content, CATALOG);
        assertThat(routes).as(file).isNotEmpty();
        return routes.get(0);
    }
}
