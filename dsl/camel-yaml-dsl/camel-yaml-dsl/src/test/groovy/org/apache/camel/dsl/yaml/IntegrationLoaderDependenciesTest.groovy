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

class IntegrationLoaderDependenciesTest extends YamlTestSupport {

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

    def "integration dependencies"() {
        when:
            loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: sql-to-log.yaml
                spec:
                  dependencies:
                    - "mvn:org.apache.commons:commons-dbcp2:2.9.0"
                    - "mvn:org.postgresql:postgresql:42.6.0"
                  flows:
                    - from:
                        uri: "sql:SELECT * FROM table1"
                        parameters:
                          dataSource: "#bean:myDatasource"
                        steps:
                          - marshal:
                              json:
                                library: Jackson
                          - log: "${body}"
                    - beans:
                        - name: myDatasource
                          type: "org.apache.commons.dbcp2.BasicDataSource"
                          properties:
                            driverClassName: "org.postgresql.Driver"
                            url: "jdbc:postgresql:localhost:5432:demo"
                            username: postgres
                            password: postgres
                          ''')
        then:
            context.routeDefinitions.size() == 1

            with (context.routeDefinitions[0]) {
                input.endpointUri == 'sql:SELECT * FROM table1?dataSource=#bean:myDatasource'
                input.lineNumber == 11;
                outputs.size() == 2
            }

            deps.size() == 2
            deps[0] == 'mvn:org.apache.commons:commons-dbcp2:2.9.0'
            deps[1] == 'mvn:org.postgresql:postgresql:42.6.0'
    }

}
