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
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition
import org.junit.jupiter.api.Assertions

class ErrorHandlerTest extends YamlTestSupport {

    def "errorHandler (ref with bean)"() {
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
                - errorHandler:
                    refErrorHandler: 
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
            context.getCamelContextExtension().getErrorHandlerFactory() instanceof RefErrorHandlerDefinition
            MockEndpoint.assertIsSatisfied(context)
    }

    def "errorHandler (ref)"() {
        setup:
            loadRoutes """
                - errorHandler:
                    refErrorHandler:
                      ref: "myErrorHandler"
            """
        when:
            context.start()
        then:
            with(context.getCamelContextExtension().getErrorHandlerFactory(), RefErrorHandlerDefinition) {
                ref == 'myErrorHandler'
            }
    }

    def "errorHandler (ref inlined)"() {
        setup:
        loadRoutes """
                - errorHandler:
                    refErrorHandler: "myErrorHandler"
            """
        when:
        context.start()
        then:
        with(context.getCamelContextExtension().getErrorHandlerFactory(), RefErrorHandlerDefinition) {
            ref == 'myErrorHandler'
        }
    }

    def "errorHandler (deadLetterChannel)"() {
        setup:
            loadRoutes """
                - errorHandler:
                    deadLetterChannel: 
                      deadLetterUri: "mock:on-error"
                      redeliveryPolicy:
                        maximumRedeliveries: 3
            """
        when:
            context.start()
        then:
            with(context.getCamelContextExtension().getErrorHandlerFactory(), DeadLetterChannelDefinition) {
                deadLetterUri == 'mock:on-error'
                redeliveryPolicy.maximumRedeliveries == "3"
            }
    }

    def "errorHandler (defaultErrorHandler)"() {
        setup:
        loadRoutes """
                - errorHandler:
                    defaultErrorHandler:
                      useOriginalMessage: true 
                      redeliveryPolicy:
                        maximumRedeliveries: 2
            """
        when:
        context.start()
        then:
        with(context.getCamelContextExtension().getErrorHandlerFactory(), DefaultErrorHandlerDefinition) {
            useOriginalMessage == "true"
            redeliveryPolicy.maximumRedeliveries == "2"
        }
    }

    def "errorHandler (no)"() {
        setup:
            loadRoutes """
                - errorHandler:
                    noErrorHandler: {}
            """
        when:
            context.start()
        then:
            context.getCamelContextExtension().getErrorHandlerFactory() instanceof NoErrorHandlerDefinition
    }

    def "errorHandler (redelivery policy ref)"() {
        setup:
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                  - name: myPolicy
                    type: org.apache.camel.processor.errorhandler.RedeliveryPolicy
                    properties:
                      maximumRedeliveries: 3
                      logStackTrace: true
                - errorHandler:
                    defaultErrorHandler:
                      useOriginalMessage: true 
                      redeliveryPolicyRef: myPolicy
                - from:
                    uri: "direct:start"
                    steps:
                      - process:
                          ref: "myFailingProcessor"
            """

        when:
        context.start()
        then:
        with(context.getCamelContextExtension().getErrorHandlerFactory(), DefaultErrorHandlerDefinition) {
            useOriginalMessage == "true"
            hasRedeliveryPolicy() == false
            redeliveryPolicyRef == "myPolicy"
        }
    }

    def "Error: duplicate errorHandler"() {
        when:
        var route = """
                - errorHandler:
                    defaultErrorHandler:
                      useOriginalMessage: true 
                      redeliveryPolicy:
                        maximumRedeliveries: 2
                    deadLetterChannel: 
                      deadLetterUri: "mock:on-error"
                      redeliveryPolicy:
                        maximumRedeliveries: 3
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch(e) {
            assert e.message.contains("2 are valid")
        }
    }

    def "Error: kebab-case: error-handler"() {
        when:
        var route = """
                - error-handler:
                    defaultErrorHandler:
                      useOriginalMessage: true 
                      redeliveryPolicyRef: myPolicy
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch(e) {
            assert e.message.contains("additional properties")
        }
    }

    def "Error: kebab-case: default-error-handler"() {
        when:
        var route = """
                - errorHandler:
                    default-error-handler:
                      useOriginalMessage: true 
                      redeliveryPolicyRef: myPolicy
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch(e) {
            assert e.message.contains("additional properties")
        }
    }

    def "Error: kebab-case: use-original-message"() {
        when:
        var route = """
                - errorHandler:
                    defaultErrorHandler:
                      use-original-message: true 
                      redeliveryPolicyRef: myPolicy
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch(e) {
            assert e.message.contains("additional properties")
        }
    }
}
