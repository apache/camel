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

class WireTapVariableTest extends YamlTestSupport {

    @Test
    void wireTapVariableSend() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: direct:send
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - wireTap:
                            uri: direct:foo
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:foo
                      steps:
                        - transform:
                            simple:
                              expression: "Bye ${body}"
                        - to:
                            uri: mock:tap
                """);

        withMock("mock:before", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("hello", "Camel");
        });
        withMock("mock:result", mock -> {
            mock.expectedBodiesReceived("World");
            mock.expectedVariableReceived("hello", "Camel");
        });
        withMock("mock:tap", mock -> {
            mock.expectedBodiesReceived("Bye Camel");
            mock.expectedVariableReceived("hello", "Camel");
        });

        context.start();

        withTemplate(t -> t.to("direct:send").withBody("World").send());

        MockEndpoint.assertIsSatisfied(context);
    }
}
