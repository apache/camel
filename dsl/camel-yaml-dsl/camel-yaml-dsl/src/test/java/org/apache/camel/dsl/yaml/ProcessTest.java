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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ProcessDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessTest extends YamlTestSupport {

    @Test
    void processDefinition() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - process:
                          ref: "myProcessor"
                """);
        var process = (ProcessDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(process.getRef()).isEqualTo("myProcessor");
    }

    @Test
    void processWithClassRef() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - process:
                          ref: "#class:org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor"
                      - to: "mock:route"
                """);
        withMock("mock:route", mock -> mock.expectedBodiesReceived("TEST"));
        context.start();
        withTemplate(t -> t.to("direct:route").withBody("test").send());
        MockEndpoint.assertIsSatisfied(context);
    }
}
