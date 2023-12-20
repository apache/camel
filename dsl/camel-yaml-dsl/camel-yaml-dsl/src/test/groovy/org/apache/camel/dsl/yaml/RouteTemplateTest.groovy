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
import org.apache.camel.dsl.yaml.support.model.MySetBody
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.apache.camel.impl.engine.DefaultRoute
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteTemplateDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.junit.jupiter.api.Assertions

class RouteTemplateTest extends YamlTestSupport {
    def "create template"() {
        when:
        loadRoutes '''
                - routeTemplate:
                    id: "myTemplate"
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            '''
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'

            route.input.endpointUri == 'direct:info'
            with(route.outputs[0], LogDefinition) {
                message == 'message'
            }
        }
    }

    def "create template with beans (#resource.location)"(Resource resource) {
        setup:
        PluginHelper.getRoutesLoader(context).loadRoutes(resource)

        withMock('mock:result') {
            expectedMessageCount 1
            expectedBodiesReceived 'HELLO'
        }
        when:
        context.addRouteFromTemplate('myId', 'myTemplate', ['directName': 'myId'])
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'
            templateBeans.size() == 1
        }

        MockEndpoint.assertIsSatisfied(context)
        where:
        resource << [
                asResource('beans', """
                        - routeTemplate:
                            id: "myTemplate"
                            beans:
                              - name: "myProcessor"
                                type: "#class:${MyUppercaseProcessor.class.name}"
                            from:
                              uri: "direct:{{directName}}"
                              steps:
                                - process:
                                    ref: "{{myProcessor}}"
                        - from:
                            uri: "direct:start"
                            steps:
                              - to: "direct:myId"
                              - to: "mock:result"
                    """),
                asResource('script', """
                        - routeTemplate:
                            id: "myTemplate"
                            beans:
                              - name: "myProcessor"
                                type: "${MyUppercaseProcessor.class.name}"
                                scriptLanguage: "groovy"
                                script: "new ${MyUppercaseProcessor.class.name}()"
                            from:
                              uri: "direct:{{directName}}"
                              steps:
                                - process:
                                    ref: "{{myProcessor}}"
                        - from:
                            uri: "direct:start"
                            steps:
                              - to: "direct:myId"
                              - to: "mock:result"
                    """),
                asResource('script-block', """
                        - routeTemplate:
                            id: "myTemplate"
                            beans:
                              - name: "myProcessor"
                                type: "org.apache.camel.Processor"
                                scriptLanguage: "groovy"
                                script: |
                                    new ${MyUppercaseProcessor.class.name}()
                            from:
                              uri: "direct:{{directName}}"
                              steps:
                                - process:
                                    ref: "{{myProcessor}}"
                        - from:
                            uri: "direct:start"
                            steps:
                              - to: "direct:myId"
                              - to: "mock:result"
                    """)
        ]
    }

    def "create template with bean and properties"() {
        setup:
        loadRoutes """                
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: "#class:${MySetBody.class.name}"
                        properties:
                          payload: "test-payload"
                    from:
                      uri: "direct:{{directName}}"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "direct:myId"
                      - to: "mock:result"
            """

        withMock('mock:result') {
            expectedMessageCount 1
            expectedBodiesReceived 'test-payload'
        }
        when:
        context.addRouteFromTemplate('myId', 'myTemplate', ['directName': 'myId'])
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'
            templateBeans.size() == 1
        }

        MockEndpoint.assertIsSatisfied(context)
    }

    def "create template with bean and property"() {
        setup:
        loadRoutes """                
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: "#class:${MySetBody.class.name}"
                        property:
                          - key: "payload"
                            value: "test-payload"
                    from:
                      uri: "direct:{{directName}}"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "direct:myId"
                      - to: "mock:result"
            """

        withMock('mock:result') {
            expectedMessageCount 1
            expectedBodiesReceived 'test-payload'
        }
        when:
        context.addRouteFromTemplate('myId', 'myTemplate', ['directName': 'myId'])
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'
            templateBeans.size() == 1
        }

        MockEndpoint.assertIsSatisfied(context)
    }

    def "create template with properties"() {
        when:
        loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                        defaultValue: "myDefaultFoo"
                        description: "myFooDescription"
                      - name: "bar"
                        description: "myBarDescription"
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            """
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'
            configurer == null

            templateParameters.any {
                it.name == 'foo' && it.defaultValue == 'myDefaultFoo' && it.description == 'myFooDescription'
            }
            templateParameters.any {
                it.name == 'bar' && it.defaultValue == null && it.description == 'myBarDescription'
            }

            route.input.endpointUri == 'direct:info'
            with(route.outputs[0], LogDefinition) {
                message == 'message'
            }
        }
    }

    def "create template with optional properties"() {
        when:
        loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                      - name: "bar"
                        required: false
                    from:
                      uri: "direct:{{foo}}"
                      steps:
                        - to: "mock:result?retainFirst={{?bar}}"
            """
        then:
        context.routeTemplateDefinitions.size() == 1

        with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
            id == 'myTemplate'
            configurer == null

            templateParameters.any {
                it.name == 'foo' && it.defaultValue == null && it.isRequired()
            }
            templateParameters.any {
                it.name == 'bar' && it.defaultValue == null && !it.isRequired()
            }

            route.input.endpointUri == 'direct:{{foo}}'
            with(route.outputs[0], ToDefinition) {
                uri == 'mock:result?retainFirst={{?bar}}'
            }
        }

        context.start()

        context.addRouteFromTemplate("myRoute1", "myTemplate", [foo: "start", bar: "1"])
        Assertions.assertNull(context.hasEndpoint("mock:result"))
        Assertions.assertNotNull(context.hasEndpoint("mock:result?retainFirst=1"))
        MockEndpoint mock = context.getEndpoint("mock:result?retainFirst=1", MockEndpoint)
        mock.expectedBodiesReceived("Hello World")
        context.createProducerTemplate().sendBody("direct:start", "Hello World");
        mock.assertIsSatisfied()
        mock.reset()

        context.addRouteFromTemplate("myRoute2", "myTemplate", [foo: "start2"])
        MockEndpoint mock2 = context.getEndpoint("mock:result", MockEndpoint)
        mock2.expectedBodiesReceived("Bye World")
        context.createProducerTemplate().sendBody("direct:start2", "Bye World");
        mock2.assertIsSatisfied()
    }

    def "create template with joor"() {
        setup:
            loadRoutes """                
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myAgg"
                            type: "org.apache.camel.AggregationStrategy"
                            scriptLanguage: "joor"
                            script: "(e1, e2) -> { return e2.getMessage().getBody(); }"
                        from:
                          uri: "direct:route"
                          steps:
                            - aggregate:
                                aggregationStrategy: "{{myAgg}}"
                                completionSize: 2
                                correlationExpression:
                                  header: "StockSymbol"
                                steps:  
                                  - to: "mock:result"
                """
            withMock('mock:result') {
                expectedMessageCount 2
                expectedBodiesReceived '101', '199'
            }
        when:
            context.addRouteFromTemplate('myId', 'myTemplate', [:])
            context.start()

            withTemplate {
                to('direct:route').withBody('99').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('101').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('200').withHeader('StockSymbol', 2).send()
                to('direct:route').withBody('199').withHeader('StockSymbol', 2).send()
            }
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'
                templateBeans.size() == 1
            }

            MockEndpoint.assertIsSatisfied(context)
    }

    def "create template with groovy"() {
        setup:
            loadRoutes """                
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myAgg"
                            type: "org.apache.camel.AggregationStrategy"
                            scriptLanguage: "groovy"
                            script: "class MaxAgg { int agg(int s1, int s2) { return Math.max(s1, s2) }}; new MaxAgg()"
                        from:
                          uri: "direct:route"
                          steps:
                            - aggregate:
                                aggregationStrategy: "{{myAgg}}"
                                completionSize: 2
                                correlationExpression:
                                  header: "StockSymbol"
                                steps:  
                                  - to: "mock:result"
                """
            withMock('mock:result') {
                expectedMessageCount 2
                expectedBodiesReceived '101', '200'
            }
        when:
            context.addRouteFromTemplate('myId', 'myTemplate', [:])
            context.start()

            withTemplate {
                to('direct:route').withBody('99').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('101').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('200').withHeader('StockSymbol', 2).send()
                to('direct:route').withBody('199').withHeader('StockSymbol', 2).send()
            }
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'
                templateBeans.size() == 1
            }

            MockEndpoint.assertIsSatisfied(context)
    }

    def "create routeTemplate with route"() {
        setup:
        loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                      - name: "bar"
                    route:
                      streamCaching: false
                      messageHistory: true
                      logMask: true
                      from:
                        uri: "direct:{{foo}}"
                        steps:
                          - to: "mock:{{bar}}"
            """
        when:
            context.addRouteFromTemplate('myId', 'myTemplate', [foo: "start", bar: "result"])
            context.start()

        then:
            with(context.routes[0], DefaultRoute) {
                it.isStreamCaching() == false
                it.isMessageHistory() == true
                it.isLogMask() == true
            }
    }

    def "create routeTemplate with prefix"() {
        setup:
        loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                      - name: "bar"
                    from:
                      uri: "direct:{{foo}}"
                      steps:
                      - choice:  
                          when:
                            - header: "foo"
                              steps:
                                - log:
                                    id: "myLog"
                                    message: "Hello World"
                          otherwise:
                            steps:
                              - to:
                                  uri: "mock:{{bar}}"
                                  id: "end"
            """
        when:
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", "aaa", parameters);

        parameters.put("foo", "two");
        parameters.put("bar", "cake");
        context.addRouteFromTemplate("second", "myTemplate", "bbb", parameters);
        context.start()

        then:
        Assertions.assertEquals(3, context.getRoute("first").filter("aaa*").size());
        Assertions.assertEquals(3, context.getRoute("second").filter("bbb*").size());
    }

    def "Error: kebab-case: route-template"() {
        when:
        var route = """
                - route-template:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                      - name: "bar"
                    from:
                      uri: "direct:{{foo}}"
                      steps:
                        - log: "{{bar}}"
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: default-value"() {
        when:
        var route = """
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                        default-value: "myDefaultFoo"
                        description: "myFooDescription"
                      - name: "bar"
                        description: "myBarDescription"
                    from:
                      uri: "direct:{{foo}}"
                      steps:
                        - log: "{{bar}}"
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }
}
