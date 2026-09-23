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

class IdempotentConsumerTest extends YamlTestSupport {

    @Test
    void idempotentConsumer() throws Exception {
        loadRoutes("""
                - beans:
                  - name: myRepo
                    type: org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository
                - from:
                    uri: "direct:route"
                    steps:
                      - idempotentConsumer:
                          simple: "${header.id}"
                          idempotentRepository: "myRepo"
                          steps:
                            - to: "mock:idempotent"
                      - to: "mock:route"
                """);

        withMock("mock:idempotent", mock -> mock.expectedBodiesReceived("a", "b", "c"));
        withMock("mock:route", mock -> mock.expectedBodiesReceived("a", "b", "a2", "b2", "c"));

        context.start();

        withTemplate(t -> {
            t.to("direct:route").withBody("a").withHeader("id", "1").send();
            t.to("direct:route").withBody("b").withHeader("id", "2").send();
            t.to("direct:route").withBody("a2").withHeader("id", "1").send();
            t.to("direct:route").withBody("b2").withHeader("id", "2").send();
            t.to("direct:route").withBody("c").withHeader("id", "3").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }
}
