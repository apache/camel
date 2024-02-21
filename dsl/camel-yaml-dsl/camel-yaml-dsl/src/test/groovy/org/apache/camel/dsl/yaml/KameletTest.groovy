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
import org.apache.camel.dsl.yaml.common.YamlDeserializationMode
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.dsl.yaml.support.model.MySetBody
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
import org.apache.camel.spi.Resource

class KameletTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "kamelet (#resource)"(Tuple<Resource> resource) {
        setup:
            addTemplate('setPayload') {
                from('kamelet:source')
                    .setBody().simple('${body}: {{payload}}')
            }

            loadRoutes(resource[0] as Resource)

            withMock('mock:kamelet') {
                expectedMessageCount 1
                expectedBodiesReceived 'a: 1'
            }
        when:
            withTemplate {
                to('direct:start').withBody('a').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)

        where:
            resource << [
                new Tuple<Resource>(
                        asResource('inline', '''
                        - from:
                            uri: "direct:start"
                            steps:
                              - kamelet: "setPayload?payload=1"
                              - to: "mock:kamelet"
                        ''')),
                new Tuple<Resource>(
                        asResource('name', '''
                        - from:
                            uri: "direct:start"
                            steps:
                              - kamelet: 
                                  name: "setPayload?payload=1"
                              - to: "mock:kamelet"
                        ''')),
                new Tuple<Resource>(
                        asResource('properties', '''
                            - from:
                                uri: "direct:start"
                                steps:
                                  - kamelet: 
                                      name: "setPayload"
                                      parameters:
                                        payload: 1
                                  - to: "mock:kamelet"
                            '''))
            ]
    }

    def "kamelet (aggregation)"() {
        setup:
            addTemplate('aggregate') {
                from('kamelet:source')
                        .aggregate()
                            .simple('${header.StockSymbol}')
                            .aggregationStrategy(new UseLatestAggregationStrategy())
                            .completionSize("{{size}}")
                            .to("kamelet:sink")
            }

            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet: 
                          name: aggregate?size=2
                          steps:
                            - to: "mock:result"
            '''

            withMock('mock:result') {
                expectedBodiesReceived '2', '4'
            }

        when:
            withTemplate {
                to('direct:route').withBody('1').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('2').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('3').withHeader('StockSymbol', 2).send()
                to('direct:route').withBody('4').withHeader('StockSymbol', 2).send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }


    def "kamelet (filter with flow)"() {
        setup:
            addTemplate('simple-filter') {
                from('kamelet:source')
                    .filter().simple('${header.foo} == "a"')
                    .to("kamelet:sink")
            }

            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet: 
                          name: "simple-filter"
                      - to: 
                          uri: "mock:result"
            '''

            withMock('mock:result') {
                expectedBodiesReceived '1', '3'
            }

        when:
            withTemplate {
                to('direct:route').withBody('1').withHeader('foo', 'a').send()
                to('direct:route').withBody('2').withHeader('foo', 'b').send()
                to('direct:route').withBody('3').withHeader('foo', 'a').send()
                to('direct:route').withBody('4').withHeader('foo', 'c').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (aggregation with flow)"() {
        setup:
            addTemplate('aggregate') {
                from('kamelet:source')
                        .aggregate()
                        .simple('${header.StockSymbol}')
                        .aggregationStrategy(new UseLatestAggregationStrategy())
                        .completionSize("{{size}}")
                        .to("kamelet:sink")
            }

            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet: aggregate?size=2
                      - to: "mock:result"
            '''

            withMock('mock:result') {
                expectedBodiesReceived '2', '4'
            }

        when:
            withTemplate {
                to('direct:route').withBody('1').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('2').withHeader('StockSymbol', 1).send()
                to('direct:route').withBody('3').withHeader('StockSymbol', 2).send()
                to('direct:route').withBody('4').withHeader('StockSymbol', 2).send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (definition with local bean)"() {
        setup:
            loadRoutes """
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: "#class:${MyUppercaseProcessor.class.name}"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'HELLO'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (definition with local bean and properties)"() {
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
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'test-payload'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (definition with local bean and property)"() {
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
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'test-payload'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (definition with default parameters)"() {
        setup:
            loadRoutes """
                - routeTemplate:
                    id: "myTemplate"  
                    parameters:
                      - name: "myParameter"
                        defaultValue: "myDefaultValue"
                        description: "myParameterDescription"
                    from: 
                      uri: "kamelet:source"
                      steps:
                        - setBody: 
                            constant: "{{myParameter}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result" 
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'myDefaultValue'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "kamelet (definition with parameters)"() {
        setup:
            loadRoutes """
                - routeTemplate:
                    id: "myTemplate"  
                    parameters:
                      - name: "myParameter"
                        defaultValue: "myDefaultValue"
                        description: "myParameterDescription"
                    from: 
                      uri: "kamelet:source"
                      steps:
                        - setBody: 
                            constant: "{{myParameter}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate?myParameter=test"
                      - to: "mock:result" 
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'test'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
