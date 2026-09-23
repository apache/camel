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
import org.apache.camel.model.ConvertBodyDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertBodyTest extends YamlTestSupport {

    @Test
    void convertBodyTo() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - convertBodyTo:
                          type: "java.lang.String"
                          charset: "UTF8"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("test"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("test".getBytes()).send());

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var convertBody = (ConvertBodyDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(convertBody.getType()).isEqualTo("java.lang.String");
        assertThat(convertBody.getCharset()).isEqualTo("UTF8");

        MockEndpoint.assertIsSatisfied(context);
    }
}
