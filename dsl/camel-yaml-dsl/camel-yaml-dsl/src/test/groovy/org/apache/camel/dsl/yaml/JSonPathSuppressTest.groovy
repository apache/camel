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

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.ChoiceDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.WhenDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class JSonPathSuppressTest extends YamlTestSupport {

    def "jsonpath-suppress definition (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
        with(context.routeDefinitions[0].outputs[0], ChoiceDefinition) {
            with(whenClauses[0], WhenDefinition) {
                expression.language == 'jsonpath'
                expression.expression == 'person.middlename'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:middle'
                }
            }
            with(otherwise.outputs[0], ToDefinition) {
                endpointUri == 'mock:other'
            }
        }
        where:
            resource << [
                asResource('expression', '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - jsonpath: "person.middlename"
                              steps:
                                - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
            '''),
                asResource('expression-block', '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - expression:
                                jsonpath: "person.middlename"
                              steps:
                                - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
                    ''')
            ]
    }

    def "supress-test-middle"() {
        setup:
        loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                          - jsonpath: 
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
            """

        withMock('mock:middle') {
            expectedMessageCount(1)
        }
        withMock('mock:other') {
            expectedMessageCount(0)
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody("{\"person\" : {\"firstname\" : \"foo\", \"middlename\" : \"foo2\", \"lastname\" : \"bar\"}}").send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "supress-test-no-middle"() {
        setup:
        loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                          - jsonpath: 
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
            """

        withMock('mock:middle') {
            expectedMessageCount(0)
        }
        withMock('mock:other') {
            expectedMessageCount(1)
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody("{\"person\" : {\"firstname\" : \"foo\", \"lastname\" : \"bar\"}}").send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "supress-test-last"() {
        setup:
        loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                          - jsonpath: 
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          - jsonpath: 
                              expression: "person.lastname"
                              suppressExceptions: true
                            steps:
                            - to: "mock:last"
                          otherwise:
                            steps:
                              - to: "mock:other"
            """

        withMock('mock:middle') {
            expectedMessageCount(0)
        }
        withMock('mock:last') {
            expectedMessageCount(1)
        }
        withMock('mock:other') {
            expectedMessageCount(0)
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody("{\"person\" : {\"firstname\" : \"foo\", \"lastname\" : \"bar\"}}").send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "Error: kebab-case: suppress-exceptions"() {
        when:
        var route = """
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                          - jsonpath: 
                              expression: "person.middlename"
                              suppress-exceptions: true
                            steps:
                            - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
            """
        then:
        try {
            loadRoutes route
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            assert e.message.contains("additional properties")
        }
    }
}
