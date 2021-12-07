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

import org.apache.camel.builder.DeadLetterChannelBuilder
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.ToDefinition
import org.apache.camel.processor.errorhandler.DeadLetterChannel

class KameletBindingLoaderTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }
/*
    def "kamelet binding from kamelet to kamelet"() {
        when:
            loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
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
                outputs.size() == 1
                with (outputs[0], ToDefinition) {
                    endpointUri == 'kamelet:log-sink'
                }
            }
    }

    def "kamelet binding from uri to kamelet"() {
        when:
        loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
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

    def "kamelet binding from uri to uri"() {
        when:
        loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
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

    def "kamelet binding steps"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1alpha1
            kind: KameletBinding
            metadata:
              name: steps-binding
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
                  name: prefix-action
                properties:
                  prefix: "Apache"
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
                  name: prefix-action
                properties:
                  prefix: "Hello"
              sink:
                uri: log:info
                ''')
        then:
        context.routeDefinitions.size() == 4

        with (context.routeDefinitions[0]) {
            routeId == 'steps-binding'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            outputs.size() == 3
            with (outputs[2], ToDefinition) {
                endpointUri == 'log:info'
            }
        }
    }

    def "kamelet binding steps kamelet uri"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1alpha1
            kind: KameletBinding
            metadata:
              name: steps-binding
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
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
            routeId == 'steps-binding'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            outputs.size() == 3
            with (outputs[1], ToDefinition) {
                endpointUri == 'mock:dummy'
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'log:info'
            }
        }
    }

    def "kamelet binding steps uri uri"() {
        when:
        loadBindings('''
            apiVersion: camel.apache.org/v1alpha1
            kind: KameletBinding
            metadata:
              name: steps-binding
            spec:
              source:
                ref:
                  kind: Kamelet
                  apiVersion: camel.apache.org/v1alpha1
                  name: timer-source
                properties:
                  message: "Camel"
              steps:
              - uri: mock:dummy
              - uri: mock:dummy2
              sink:
                uri: log:info
                ''')
        then:
        context.routeDefinitions.size() == 2

        with (context.routeDefinitions[0]) {
            routeId == 'steps-binding'
            input.endpointUri == 'kamelet:timer-source?message=Camel'
            outputs.size() == 3
            with (outputs[0], ToDefinition) {
                endpointUri == 'mock:dummy'
            }
            with (outputs[1], ToDefinition) {
                endpointUri == 'mock:dummy2'
            }
            with (outputs[2], ToDefinition) {
                endpointUri == 'log:info'
            }
        }
    }

    def "kamelet binding from kamelet to strimzi"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.removeComponent("kafka")
        context.addComponent("kafka", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
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
*/
    def "kamelet binding with error handler"() {
        when:

        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"))

        loadBindings('''
                apiVersion: camel.apache.org/v1alpha1
                kind: KameletBinding
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
                      apiVersion: camel.apache.org/v1alpha1
                      name: log-sink
                  errorHandler:
                    dead-letter-channel:
                      endpoint:
                        ref:
                          kind: Kamelet
                          apiVersion: camel.apache.org/v1alpha1
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
            errorHandlerFactory instanceof DeadLetterChannelBuilder
            var eh = errorHandlerFactory as DeadLetterChannelBuilder
            eh.deadLetterUri == 'kamelet:error-handler?kafkaTopic=my-first-test&logMessage=ERROR%21&kafkaServiceAccountId=scott&kafkaBrokers=my-broker&kafkaServiceAccountSecret=tiger'
            eh.redeliveryPolicy.maximumRedeliveries == 1
            eh.redeliveryPolicy.redeliveryDelay == 2000
            routeId == 'timer-event-source'
            input.endpointUri == 'kamelet:timer-source?message=Hello+world%21'
            outputs.size() == 1
            with (outputs[0], ToDefinition) {
                endpointUri == 'kamelet:log-sink'
            }
        }
    }

}
