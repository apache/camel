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
import org.junit.jupiter.api.Test;

class PollEnrichVariableTest extends YamlTestSupport {

    @Test
    void pollEnrichVariableReceive() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - pollEnrich:
                            constant: "seda:foo"
                            timeout: "1000"
                            variableReceive: bye
                        - to:
                            uri: mock:after
                        - setBody:
                            simple: ${variable:bye}
                        - to:
                            uri: mock:result
                """);

        withMock("mock:after", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("bye", "Bye World");
        });
        withMock("mock:result", mock -> {
            mock.expectedBodiesReceived("Bye World");
            mock.expectedVariableReceived("bye", "Bye World");
        });

        context.start();

        withTemplate(t -> {
            t.to("seda:foo").withBody("Bye World").send();
            t.to("direct:receive").withBody("World").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }
}
