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
package org.apache.camel.dsl.yaml;

import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipeLoaderErrorHandlerTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    @Test
    void pipeWithKameletErrorHandler() throws Exception {
        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"));

        loadBindings("""
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
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(4);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getErrorHandlerFactory()).isNotNull();
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(DeadLetterChannelDefinition.class);
        DeadLetterChannelDefinition eh = (DeadLetterChannelDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getDeadLetterUri()).isEqualTo(
                "kamelet:error-handler?kafkaTopic=my-first-test&logMessage=ERROR!&kafkaServiceAccountId=scott&kafkaBrokers=my-broker&kafkaServiceAccountSecret=tiger");
        assertThat(eh.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("1");
        assertThat(eh.getRedeliveryPolicy().getRedeliveryDelay()).isEqualTo("2000");
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink");
    }

    @Test
    void pipeWithErrorHandlerMoveToDlq() throws Exception {
        context.getRegistry().bind("chaos", (Processor) exchange -> {
            throw new IllegalArgumentException("Forced");
        });

        loadBindings("""
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
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        MockEndpoint mock = context.getEndpoint("mock:dead", MockEndpoint.class);
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getErrorHandlerFactory()).isNotNull();
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(DeadLetterChannelDefinition.class);
        DeadLetterChannelDefinition eh = (DeadLetterChannelDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getDeadLetterUri()).isEqualTo("mock:dead");
        assertThat(eh.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("3");
        assertThat(eh.getRedeliveryPolicy().getRedeliveryDelay()).isEqualTo("100");
    }

    @Test
    void pipeWithLogErrorHandler() throws Exception {
        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"));

        loadBindings("""
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
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getErrorHandlerFactory()).isNotNull();
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(DefaultErrorHandlerDefinition.class);
        DefaultErrorHandlerDefinition eh = (DefaultErrorHandlerDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("1");
        assertThat(eh.getRedeliveryPolicy().getRedeliveryDelay()).isEqualTo("2000");
        assertThat(eh.getUseOriginalMessage()).isEqualTo("true");
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink");
    }

    @Test
    void pipeWithNoneErrorHandler() throws Exception {
        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.addComponent("kafka", context.getComponent("stub"));

        loadBindings("""
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
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getErrorHandlerFactory()).isNotNull();
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(NoErrorHandlerDefinition.class);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink");
    }
}
