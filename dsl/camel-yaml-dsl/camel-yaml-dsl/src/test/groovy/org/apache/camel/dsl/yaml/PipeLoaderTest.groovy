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
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.TransformDataTypeDefinition

class PipeLoaderTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "Pipe from kamelet to kamelet with pipe.yaml extension"() {
        when:
            loadBindingsExt("pipe.yaml", '''
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
            ''')
        then:
            context.routeDefinitions.size() == 3

            with (context.routeDefinitions[0]) {
                routeId == 'timer-event-source'
                input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
                input.lineNumber == 7
                outputs.size() == 1
                with (outputs[0], ToDefinition) {
                    endpointUri == 'kamelet:log-sink'
                    lineNumber == 14
                }
            }
    }

    def "Pipe from uri to kamelet"() {
        when:
        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    uri: timer:foo
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'timer:foo'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
            }
        }
    }

    def "Pipe from uri to uri"() {
        when:
        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    uri: timer:foo
                  sink:
                    uri: log:bar
            ''')
        then:
        context.routeDefinitions.size() == 1

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'timer:foo'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'log:bar'
            }
        }
    }

    def "Pipe steps"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1
            kind: Pipe
            metadata:
              name: steps-pipe
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: prefix-action
                properties:
                  prefix: "Apache"
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: prefix-action
                properties:
                  prefix: "Hello"
              sink:
                uri: log:info
                ''')
        then:
        context.routeDefinitions.size() == 4

        with (context.routeDefinitions[0]) {
            routeId == 'steps-pipe'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            input.lineNumber == 7
            outputs.size() == 3
            with (outputs[0], KameletDefinition) {
                name == 'prefix-action?prefix=Apache'
                lineNumber == 14
            }
            with (outputs[1], KameletDefinition) {
                name == 'prefix-action?prefix=Hello'
                lineNumber == 20
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'log:info'
                lineNumber == 27
            }
        }
    }

    def "Pipe steps kamelet uri"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1
            kind: Pipe
            metadata:
              name: steps-pipe
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: prefix-action
                properties:
                  prefix: "Apache"
              - uri: mock:dummy
              sink:
                uri: log:info
                ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'steps-pipe'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            outputs.size() == 3
            with (outputs[0], KameletDefinition) {
                name == 'prefix-action?prefix=Apache'
            }
            with (outputs[1], ToDefinition) {
                endpointUri == 'mock:dummy'
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'log:info'
            }
        }
    }

    def "Pipe steps uri uri"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1
            kind: Pipe
            metadata:
              name: steps-pipe
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - uri: mock:dummy
              - uri: kamelet:prefix-action?prefix=Apache
              - uri: mock:dummy2
                properties:
                  reportGroup: 5
              sink:
                uri: log:info
                ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'steps-pipe'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            outputs.size() == 4
            with (outputs[0], ToDefinition) {
                endpointUri == 'mock:dummy'
            }
            with (outputs[1], KameletDefinition) {
                name == 'prefix-action?prefix=Apache'
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'mock:dummy2?reportGroup=5'
            }
            with (outputs[3], ToDefinition) {
                endpointUri == 'log:info'
            }
        }
    }

    def "Pipe from kamelet to strimzi"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.removeComponent("kafka")
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
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kafka:my-topic'
            }
        }
    }

    // TODO: Fix me later
    /*
    def "Pipe with error handler"() {
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
    }*/

    def "Pipe from kamelet to knative channel"() {
        when:

        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative")
        context.addComponent("knative", context.getComponent("stub"))

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
                      kind: InMemoryChannel
                      apiVersion: messaging.knative.dev/v1
                      name: my-messages
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'knative:channel/my-messages'
            }
        }
    }

    def "Pipe from knative channel to kamelet"() {
        when:

        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative")
        context.addComponent("knative", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: knative-event-source
                spec:
                  source:
                    ref:
                      kind: InMemoryChannel
                      apiVersion: messaging.knative.dev/v1
                      name: my-messages
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'knative-event-source'
            input.endpointUri == 'knative:channel/my-messages'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink?showHeaders=true'
            }
        }
    }

    def "Pipe from kamelet to knative broker"() {
        when:

        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative")
        context.addComponent("knative", context.getComponent("stub"))

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
                      kind: Broker
                      apiVersion: eventing.knative.dev/v1
                      name: foo-broker
                    properties:
                      type: org.apache.camel.event.messages
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'knative:event/org.apache.camel.event.messages?kind=Broker&name=foo-broker'
            }
        }
    }

    def "Pipe from knative broker to kamelet"() {
        when:

        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative")
        context.addComponent("knative", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: knative-event-source
                spec:
                  source:
                    ref:
                      kind: Broker
                      apiVersion: eventing.knative.dev/v1
                      name: foo-broker
                    properties:
                      type: org.apache.camel.event.messages
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
            ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'knative-event-source'
            input.endpointUri == 'knative:event/org.apache.camel.event.messages?kind=Broker&name=foo-broker'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink?showHeaders=true'
            }
        }
    }

    def "kamelet start route"() {
        when:
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
                      name: route-timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
            ''')
        then:
        context.routeDefinitions.size() == 3

        // global stream caching enabled
        context.isStreamCaching() == true

        with (context.routeDefinitions[1]) {
            template == true
            // stream-cache is disabled in the kamelet
            streamCache == "false"
            messageHistory == "true"
        }
    }

    def "Pipe no sink"() {
        when:
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
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-action
            ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            input.lineNumber == 7
            outputs.size() == 1
            with (outputs[0], KameletDefinition) {
                name == 'log-action'
                lineNumber == 14
            }
        }
    }

    def "Pipe with input/output data types"() {
        when:
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
                    dataTypes:
                      in:
                        format: text/plain
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      out:
                        format: application/octet-stream
            ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            input.lineNumber == 7
            inputType.urn == 'text/plain'
            outputType.urn == 'application/octet-stream'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
                lineNumber == 17
            }
        }
    }

    def "Pipe with input/output data types and schemes"() {
        when:
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
                    dataTypes:
                      in:
                        scheme: camel
                        format: text/plain
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      out:
                        scheme: camel
                        format: application/octet-stream
            ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            input.lineNumber == 7
            inputType.urn == 'camel:text/plain'
            outputType.urn == 'camel:application/octet-stream'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
                lineNumber == 18
            }
        }
    }

    def "Pipe with data type transformation"() {
        when:
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
                    dataTypes:
                      out:
                        format: application/octet-stream
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      in:
                        format: text/plain
            ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            input.lineNumber == 7
            outputs.size() == 3
            with (outputs[0], TransformDataTypeDefinition) {
                toType == 'application/octet-stream'
                lineNumber == -1
            }
            with (outputs[1], TransformDataTypeDefinition) {
                toType == 'text/plain'
                lineNumber == -1
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
                lineNumber == 17
            }
        }
    }

    def "Pipe with data type scheme transformation"() {
        when:
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
                    dataTypes:
                      out:
                        scheme: camel
                        format: application/octet-stream
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      in:
                        scheme: camel
                        format: text/plain
            ''')
        then:
        context.routeDefinitions.size() == 3

        with (context.routeDefinitions[0]) {
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            input.lineNumber == 7
            outputs.size() == 3
            with (outputs[0], TransformDataTypeDefinition) {
                toType == 'camel:application/octet-stream'
                lineNumber == -1
            }
            with (outputs[1], TransformDataTypeDefinition) {
                toType == 'camel:text/plain'
                lineNumber == -1
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
                lineNumber == 18
            }
        }
    }

}
