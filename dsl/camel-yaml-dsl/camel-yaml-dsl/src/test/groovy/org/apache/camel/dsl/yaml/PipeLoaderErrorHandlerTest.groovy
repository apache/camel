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

import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition

class PipeLoaderErrorHandlerTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "Pipe with kamelet error handler"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source                  
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                  errorHandler:
                    sink:
                      endpoint:
                        ref:
                          kind: Kamelet
                          apiVersion: camel.apache.org/v1
                          name: error-handler
                        properties:
                          log-message: "ERROR!"
                          kafka-brokers: my-broker
                          kafka-topic: my-first-test
                          kafka-service-account-id: scott
                          kafka-service-account-secret: tiger
                      parameters:
                        maximumRedeliveries: 1
                        redeliveryDelay: 2000    
                    ''')
        then:
        context.routeDefinitions.size() == 4

        with (context.routeDefinitions[0]) {
            errorHandlerFactory != null
            errorHandlerFactory instanceof DeadLetterChannelDefinition
            var eh = errorHandlerFactory as DeadLetterChannelDefinition
            eh.deadLetterUri == 'kamelet:error-handler?kafkaTopic=my-first-test&logMessage=ERROR%21&kafkaServiceAccountId=scott&kafkaBrokers=my-broker&kafkaServiceAccountSecret=tiger'
            eh.redeliveryPolicy.maximumRedeliveries == "1"
            eh.redeliveryPolicy.redeliveryDelay == "2000"
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
            }
        }
    }

    def "Pipe with error handler move to dlq"() {
        when:

        context.registry.bind 'chaos', new Processor() {
            @Override
            void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Forced")
            }
        }

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source                  
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  steps:
                    - uri: bean:chaos  
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                  errorHandler:
                    sink:
                      endpoint:
                        uri: mock:dead
                      parameters:
                        maximumRedeliveries: 3
                        redeliveryDelay: 100    
                    ''')
        then:
        context.routeDefinitions.size() == 3

        MockEndpoint mock = context.getEndpoint("mock:dead", MockEndpoint.class)
        mock.expectedMinimumMessageCount(1)

        mock.assertIsSatisfied()

        with (context.routeDefinitions[0]) {
            errorHandlerFactory != null
            errorHandlerFactory instanceof DeadLetterChannelDefinition
            var eh = errorHandlerFactory as DeadLetterChannelDefinition
            eh.deadLetterUri == 'mock:dead'
            eh.redeliveryPolicy.maximumRedeliveries == "3"
            eh.redeliveryPolicy.redeliveryDelay == "100"
        }
    }

    def "Pipe with log error handler"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source                  
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                  errorHandler:
                    log:
                      parameters:
                        use-original-message: true
                        maximumRedeliveries: 1
                        redeliveryDelay: 2000    
                    ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            errorHandlerFactory != null
            errorHandlerFactory instanceof DefaultErrorHandlerDefinition
            var eh = errorHandlerFactory as DefaultErrorHandlerDefinition
            eh.redeliveryPolicy.maximumRedeliveries == "1"
            eh.redeliveryPolicy.redeliveryDelay == "2000"
            eh.getUseOriginalMessage() == "true"
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
            }
        }
    }

    def "Pipe with none error handler"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source                  
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                  errorHandler:
                    none:
                    ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            errorHandlerFactory != null
            errorHandlerFactory instanceof NoErrorHandlerDefinition
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
            }
        }
    }

}
