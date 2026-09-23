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

import java.util.Map;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FromVariableTest extends YamlTestSupport {

    @Test
    void fromVariable() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    variableReceive: "myKey"
                    steps:
                      - setHeader:
                          name: foo
                          constant: "456"
                      - setHeader:
                          name: bar
                          constant: "Murphy"
                      - transform:
                          simple: "Bye ${body}"
                      - to: "mock:foo"
                      - setBody:
                          simple: "${variable:myKey}"
                      - to: "mock:result"
                """);

        withMock("mock:foo", mock -> {
            mock.expectedBodiesReceived("Bye ");
            mock.whenAnyExchangeReceived(e -> {
                Map<?, ?> m = e.getVariable("header:myKey", Map.class);
                assertThat(m).isNotNull();
                assertThat(m.size()).isEqualTo(1);
                assertThat(m.get("foo")).isEqualTo(123);
            });
        });
        withMock("mock:result", mock -> mock.expectedBodiesReceived("World"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("World").withHeader("foo", 123).send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeFromVariable() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: "direct:start"
                      variableReceive: "myKey"
                      steps:
                        - setHeader:
                            name: foo
                            constant: "456"
                        - setHeader:
                            name: bar
                            constant: "Murphy"
                        - transform:
                            simple: "Bye ${body}"
                        - to: "mock:foo"
                        - setBody:
                            simple: "${variable:myKey}"
                        - to: "mock:result"
                """);

        withMock("mock:foo", mock -> {
            mock.expectedBodiesReceived("Bye ");
            mock.whenAnyExchangeReceived(e -> {
                Map<?, ?> m = e.getVariable("header:myKey", Map.class);
                assertThat(m).isNotNull();
                assertThat(m.size()).isEqualTo(1);
                assertThat(m.get("foo")).isEqualTo(123);
            });
        });
        withMock("mock:result", mock -> mock.expectedBodiesReceived("World"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("World").withHeader("foo", 123).send());

        MockEndpoint.assertIsSatisfied(context);
    }
}
