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
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition
import org.apache.camel.model.errorhandler.ErrorHandlerRefDefinition
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition

class ErrorHandlerTest extends YamlTestSupport {

    def "error-handler (ref with bean)"() {
        setup:
            loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                  - name: myErrorHandler
                    type: org.apache.camel.model.errorhandler.DeadLetterChannelDefinition
                    properties:
                      dead-letter-uri: "mock:on-error"
                      redelivery-delay: 0
                - error-handler:
                    ref: "myErrorHandler"
                - from:
                    uri: "direct:start"
                    steps:
                      - process:
                          ref: "myFailingProcessor"
            """

            withMock('mock:on-error') {
                expectedMessageCount = 1
            }

        when:
            context.start()

            withTemplate {
                to('direct:start').withBody('hello').send()
            }
        then:
            context.errorHandlerFactory instanceof ErrorHandlerRefDefinition
            MockEndpoint.assertIsSatisfied(context)
    }

    def "error-handler (ref)"() {
        setup:
            loadRoutes """
                - error-handler:
                    ref: "myErrorHandler"
            """
        when:
            context.start()
        then:
            with(context.errorHandlerFactory, ErrorHandlerRefDefinition) {
                ref == 'myErrorHandler'
            }
    }

    def "error-handler (log)"() {
        setup:
            loadRoutes """
                - error-handler:
                    log: 
                      use-original-message: true
            """
        when:
            context.start()
        then:
            with(context.errorHandlerFactory, DefaultErrorHandlerDefinition) {
                useOriginalMessage
            }
    }

    def "error-handler (dead-letter-channel)"() {
        setup:
            loadRoutes """
                - error-handler:
                    dead-letter-channel: 
                      dead-letter-uri: "mock:on-error"
            """
        when:
            context.start()
        then:
            with(context.errorHandlerFactory, DeadLetterChannelDefinition) {
                deadLetterUri == 'mock:on-error'
            }
    }

    def "error-handler (none)"() {
        setup:
            loadRoutes """
                - error-handler:
                    none: {}
            """
        when:
            context.start()
        then:
            context.errorHandlerFactory instanceof NoErrorHandlerDefinition
    }

}
