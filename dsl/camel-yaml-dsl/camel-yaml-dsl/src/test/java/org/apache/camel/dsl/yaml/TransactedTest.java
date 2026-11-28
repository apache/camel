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

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.spi.Policy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactedTest extends YamlTestSupport {

    @Test
    void transactedDefinition() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - transacted:
                          ref: "required"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var transacted = (TransactedDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(transacted.getRef()).isEqualTo("required");
    }

    @Test
    void transactedKeepsTheStepOrder() throws Exception {
        context.getRegistry().bind("myPolicy", new Policy() {
            @Override
            public void beforeWrap(Route route, NamedNode definition) {
                // noop
            }

            @Override
            public Processor wrap(Route route, Processor processor) {
                return processor;
            }
        });

        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - setBody:
                          simple: "${body}-a"
                      - transacted:
                          ref: "myPolicy"
                          steps:
                            - setBody:
                                simple: "${body}-b"
                      - setBody:
                          simple: "${body}-c"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("x-a-b-c"));

        context.start();

        // the transacted wraps the entire route, and the steps keep the order they are written in
        var outputs = context.getRouteDefinitions().get(0).getOutputs();
        assertThat(outputs).hasSize(1);
        assertThat(outputs.get(0)).isInstanceOf(TransactedDefinition.class);

        withTemplate(t -> t.to("direct:start").withBody("x").send());

        MockEndpoint.assertIsSatisfied(context);
    }
}
