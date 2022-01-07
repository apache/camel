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
import org.apache.camel.model.KameletDefinition

class KameletIntegrationLoaderTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "kamelet integration"() {
        when:
            loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar
                spec:
                  flows:
                    - from:
                        uri: 'kamelet:timer-source'
                        steps:
                          - kamelet:
                              name: log-sink
                              parameters:
                                showStreams: false
                                showHeaders: false
                        parameters:
                          message: Hello Camel K
                          period: 1234
                      ''')
        then:
            context.routeDefinitions.size() == 3

            with (context.routeDefinitions[0]) {
                input.endpointUri == 'kamelet:timer-source?message=Hello Camel K&period=1234'
                input.lineNumber == 8;
                outputs.size() == 1
                with (outputs[0], KameletDefinition) {
                    name == 'log-sink?showStreams=false&showHeaders=false'
                    lineNumber == 11;
                }
            }
    }

}
