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
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.spi.PropertiesComponent

class IntegrationLoaderTest extends YamlTestSupport {

    def "integration configuration"() {
        when:
            loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar
                spec:
                  configuration:
                    - type: property
                      value: camel.component.seda.queueSize = 123
                    - type: property
                      value: camel.component.seda.default-block-when-full = true
                  flows:
                    - from:
                        uri: "seda:foo"
                        steps:    
                          - log:
                             logging-level: "INFO"
                             message: "test"
                             log-name: "yaml"
                          - to: "mock:result"   
                          ''')
        then:
            context.routeDefinitions.size() == 1

            PropertiesComponent pc = context.getPropertiesComponent()
            pc.resolveProperty("camel.component.seda.queueSize").get() == "123"
            pc.resolveProperty("camel.component.seda.default-block-when-full").get() == "true"
    }

    def "integration trait configuration"() {
        when:
        loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar2
                spec:
                  traits:
                    camel:
                      configuration:
                        properties:
                          - camel.component.seda.queueSize = 456
                          - camel.component.seda.default-block-when-full = true
                  flows:
                    - from:
                        uri: "seda:foo"
                        steps:    
                          - log:
                             logging-level: "INFO"
                             message: "test"
                             log-name: "yaml"
                          - to: "mock:result"   
                          ''')
        then:
        context.routeDefinitions.size() == 1

        PropertiesComponent pc = context.getPropertiesComponent()
        pc.resolveProperty("camel.component.seda.queueSize").get() == "456"
        pc.resolveProperty("camel.component.seda.default-block-when-full").get() == "true"
    }

    def "integration env configuration"() {
        when:
        loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar2
                spec:
                  traits:
                    camel:
                      configuration:
                        properties:
                          - camel.component.seda.queueSize = 456
                          - camel.component.seda.default-block-when-full = true
                    environment:
                      configuration:
                        vars:
                          - TEST_MESSAGE = Hello World    
                  flows:
                    - from:
                        uri: "seda:foo"
                        steps:    
                          - log:
                             logging-level: "INFO"
                             message: "{{TEST_MESSAGE}}"
                             log-name: "yaml"
                          - to: "mock:result"   
                          ''')
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0].outputs[0], LogDefinition) {
            loggingLevel == 'INFO'
            message == '{{TEST_MESSAGE}}'
            logName == 'yaml'
        }

        PropertiesComponent pc = context.getPropertiesComponent()
        pc.resolveProperty("camel.component.seda.queueSize").get() == "456"
        pc.resolveProperty("camel.component.seda.default-block-when-full").get() == "true"
        pc.resolveProperty("TEST_MESSAGE").get() == "Hello World"
    }

    def "integration"() {
        when:
        loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar3
                spec:
                  flows:
                    - from:
                        uri: "seda:foo"
                        steps:    
                          - to: "mock:result"   
                          ''')
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0].outputs[0], ToDefinition) {
            uri == "mock:result"
        }
    }

    def "integrationTwo"() {
        when:
        loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar3
                spec:
                  flows:
                    - from:
                        uri: "seda:foo"
                        steps:    
                          - to: "mock:result"   
                    - from:
                        uri: "seda:foo2"
                        steps:    
                          - to: "mock:result2"   
                          ''')
        then:
        context.routeDefinitions.size() == 2

        with(context.routeDefinitions[0].outputs[0], ToDefinition) {
            uri == "mock:result"
        }
        with(context.routeDefinitions[1].outputs[0], ToDefinition) {
            uri == "mock:result2"
        }
    }

    def "integration with route"() {
        when:
        loadIntegrations('''
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: foobar3
                spec:
                  flows:
                    - route:
                        id: myRoute
                        from:
                          uri: "seda:foo"
                          steps:    
                            - to: "mock:result"   
                          ''')
        then:
        context.routeDefinitions.size() == 1

        context.routeDefinitions[0].id == "myRoute"

        with(context.routeDefinitions[0].outputs[0], ToDefinition) {
            uri == "mock:result"
        }
    }

}
