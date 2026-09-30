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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.spi.DependencyStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KameletLoaderDependenciesTest extends YamlTestSupport {

    private final List<String> deps = new ArrayList<>();

    @Override
    public void doSetup() throws Exception {
        context.getRegistry().bind("myDep", (DependencyStrategy) dependency -> deps.add(dependency));
        context.start();
    }

    @Test
    void kameletDependencies() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: myDependency
                spec:
                  definition:
                    properties:
                      payload:
                        title: The Payload
                        type: string
                  dependencies:
                    - "camel:jackson"
                    - "camel:kamelet"
                    - "camel:sql"
                    - "mvn:org.apache.commons:commons-dbcp2:2.9.0"
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - setBody:
                            constant: "{{payload}}"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        assertThat(deps.size()).isEqualTo(4);
        assertThat(deps.get(0)).isEqualTo("camel:jackson");
        assertThat(deps.get(1)).isEqualTo("camel:kamelet");
        assertThat(deps.get(2)).isEqualTo("camel:sql");
        assertThat(deps.get(3)).isEqualTo("mvn:org.apache.commons:commons-dbcp2:2.9.0");
    }
}
