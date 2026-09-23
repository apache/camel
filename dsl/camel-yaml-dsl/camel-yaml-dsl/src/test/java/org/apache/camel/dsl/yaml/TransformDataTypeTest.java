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

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDataTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransformDataTypeTest extends YamlTestSupport {

    @Test
    void transformWithDataTypes() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - transformDataType:
                          fromType: "text/plain"
                          toType: "application-octet-stream"
                      - to: "mock:result"
                """);

        var transform = (TransformDataTypeDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(transform.getFromType()).isEqualTo("text/plain");
        assertThat(transform.getToType()).isEqualTo("application-octet-stream");

        var to = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(1);
        assertThat(to.getEndpointUri()).isEqualTo("mock:result");
    }
}
