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

import org.apache.camel.FailedToCreateRouteException
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.common.YamlDeserializationMode
import org.apache.camel.dsl.yaml.support.YamlTestSupport

class AggregateTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def 'aggregate'() {
        setup:
            loadRoutes '''
                - beans:
                  - name: myAggregatorStrategy
                    type: org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "myAggregatorStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:  
                            - to: "mock:route"
            '''

            withMock('mock:route') {
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

    def 'aggregate (flow)'() {
        setup:
            loadRoutes '''
                - beans:
                  - name: myAggregatorStrategy
                    type: org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "myAggregatorStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                      - to: "mock:route"
            '''

            withMock('mock:route') {
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

    def 'aggregate (strategy-ref class)'() {
        setup:
        loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "#class:org.apache.camel.processor.aggregate.UseLatestAggregationStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:  
                            - to: "mock:route"
            '''

        withMock('mock:route') {
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
}
