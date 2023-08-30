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
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.apache.camel.model.RouteDefinition

class TemplatedRouteTest extends YamlTestSupport {

    def "create templated route"() {
        setup:
        loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    from:
                      uri: "direct:{{directName}}"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                        - to: "mock:result"
                - templatedRoute:
                    routeId: "myRoute"
                    routeTemplateRef: "myTemplate"
                    parameters:
                      - name: "directName"
                        value: "foo"
                    beans:
                      - name: "myProcessor"
                        type: "groovy"
                        script: |
                            new ${MyUppercaseProcessor.class.name}()
                - templatedRoute:
                    routeId: "myRoute2"
                    routeTemplateRef: "myTemplate"
                    parameters:
                      - name: "directName"
                        value: "foo2"
                    beans:
                      - name: "myProcessor"
                        type: "groovy"
                        beanType: "org.apache.camel.Processor"
                        script: "new ${MyUppercaseProcessor.class.name}()"                 
            """
        withMock('mock:result') {
            expectedMessageCount 2
            expectedBodiesReceived 'HELLO', "WORLD"
        }
        when:
        context.start()
        withTemplate {
            to('direct:foo').withBody('hello').send()
            to('direct:foo2').withBody('world').send()
        }
        then:
        context.routeTemplateDefinitions.size() == 1
        context.routeDefinitions.size() == 2

        with(context.routeDefinitions[0], RouteDefinition) {
            routeId == 'myRoute'
        }
        with(context.routeDefinitions[1], RouteDefinition) {
            routeId == 'myRoute2'
        }
        MockEndpoint.assertIsSatisfied(context)
    }

    def "create templated-route"() {
        setup:
        loadRoutes """
                - route-template:
                    id: "myTemplate"
                    from:
                      uri: "direct:{{directName}}"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                        - to: "mock:result"
                - templatedRoute:
                    routeId: "myRoute"
                    routeTemplateRef: "myTemplate"
                    parameters:
                      - name: "directName"
                        value: "foo"
                    beans:
                      - name: "myProcessor"
                        type: "groovy"
                        script: |
                            new ${MyUppercaseProcessor.class.name}()
                - templatedRoute:
                    routeId: "myRoute2"
                    routeTemplateRef: "myTemplate"
                    parameters:
                      - name: "directName"
                        value: "foo2"
                    beans:
                      - name: "myProcessor"
                        type: "groovy"
                        bean-type: "org.apache.camel.Processor"
                        script: "new ${MyUppercaseProcessor.class.name}()"                 
            """
        withMock('mock:result') {
            expectedMessageCount 2
            expectedBodiesReceived 'HELLO', "WORLD"
        }
        when:
        context.start()
        withTemplate {
            to('direct:foo').withBody('hello').send()
            to('direct:foo2').withBody('world').send()
        }
        then:
        context.routeTemplateDefinitions.size() == 1
        context.routeDefinitions.size() == 2

        with(context.routeDefinitions[0], RouteDefinition) {
            routeId == 'myRoute'
        }
        with(context.routeDefinitions[1], RouteDefinition) {
            routeId == 'myRoute2'
        }
        MockEndpoint.assertIsSatisfied(context)
    }

}
