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
package org.apache.camel.dsl.yaml

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.spi.DependencyStrategy

class KameletBindingLoaderIntegrationSpecTest extends YamlTestSupport {

    var Set<String> deps = new LinkedHashSet<>()

    @Override
    def doSetup() {
        context.registry.bind("myDep", new DependencyStrategy() {
            @Override
            void onDependency(String dependency) {
                deps.add(dependency);
            }
        })

        context.start()
    }

    def "binding with integration spec"() {
        when:
        loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
                metadata:
                  name: timer-event-source
                spec:
                  integration:
                    dependencies:
                    - "camel:cloudevents"
                    traits:
                      camel:
                        configuration:
                          properties:
                          - "foo=howdy"
                          - "bar=123"
                      environment:
                        configuration:
                          vars:
                          - "MY_ENV=cheese"   
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
            ''')
        then:
        context.routeDefinitions.size() == 3

        context.resolvePropertyPlaceholders("{{foo}}") == "howdy"
        context.resolvePropertyPlaceholders("{{bar}}") == "123"
        context.resolvePropertyPlaceholders("{{MY_ENV}}") == "cheese"

        with(deps) {
            deps.contains("camel:core")
            deps.contains("camel:kamelet")
            deps.contains("camel:cloudevents")
        }
    }

}
