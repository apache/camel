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

class KameletLoaderDependenciesTest extends YamlTestSupport {

    var List<String> deps = new ArrayList<>()

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

    def "kamelet dependencies"() {
        when:
            loadKamelets('''
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
                        - set-body:
                            constant: "{{payload}}"
          ''')
        then:
            context.routeTemplateDefinitions.size() == 1

            deps.size() == 4
            deps[0] == 'camel:jackson'
            deps[1] == 'camel:kamelet'
            deps[2] == 'camel:sql'
            deps[3] == 'mvn:org.apache.commons:commons-dbcp2:2.9.0'
    }

}
