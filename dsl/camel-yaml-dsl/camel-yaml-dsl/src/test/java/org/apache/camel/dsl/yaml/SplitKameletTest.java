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

class SplitKameletTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    @Test
    void split() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - to:
                          uri: kamelet
                          parameters:
                            templateId: mySplit
                            myExpression: "${body.split('@')}"
                      - to:
                          uri: mock:done
                """);

        withMock("mock:split", mock -> {
            mock.expectedMessageCount(3);
            mock.expectedBodiesReceived("a", "b", "c");
        });
        withMock("mock:done", mock -> mock.expectedMessageCount(1));

        withTemplate(t -> t.to("direct:route").withBody("a@b@c").send());

        MockEndpoint.assertIsSatisfied(context);
    }
}
