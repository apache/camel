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

import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27362: the option names under parameters: are turned into camelCase, but the key of a map option (after a
 * multiValue prefix such as scheduler. or properties.) is kept as written, as it is passed on as it is.
 */
class ParametersMultiValueTest extends YamlTestSupport {

    @Test
    void keyAfterMultiValuePrefixIsKept() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersMultiValueTest/out
                          parameters:
                            auto-create: false
                            scheduler.pool-size: 2
                            scheduler.my-option: hello
                """);

        assertThat(toUri()).isEqualTo("file:target/data/ParametersMultiValueTest/out"
                                      + "?autoCreate=false&scheduler.pool-size=2&scheduler.my-option=hello");
        FileEndpoint file = context.getEndpoint(toUri(), FileEndpoint.class);
        assertThat(file.isAutoCreate()).isFalse();
        assertThat(file.getSchedulerProperties()).containsEntry("pool-size", "2").containsEntry("my-option", "hello");
    }

    @Test
    void keyOfMapOptionIsKept() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersMultiValueTest/out
                          parameters:
                            scheduler-properties:
                              pool-size: 2
                              my-option: hello
                """);

        assertThat(toUri()).isEqualTo("file:target/data/ParametersMultiValueTest/out"
                                      + "?scheduler.pool-size=2&scheduler.my-option=hello");
        FileEndpoint file = context.getEndpoint(toUri(), FileEndpoint.class);
        assertThat(file.getSchedulerProperties()).containsEntry("pool-size", "2").containsEntry("my-option", "hello");
    }

    @Test
    void otherDottedKeyIsCamelCaseAsBefore() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file:target/data/ParametersMultiValueTest/out
                          parameters:
                            Scheduler.pool-size: 2
                """);

        // not a multiValue prefix (case matters), so the key is in camelCase as before
        assertThat(toUri()).isEqualTo("file:target/data/ParametersMultiValueTest/out?Scheduler.poolSize=2");
    }

    @Test
    void uriIsTheScheme() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:start
                    steps:
                      - to:
                          uri: file
                          parameters:
                            directory-name: target/data/ParametersMultiValueTest/out
                            auto-create: false
                            scheduler.my-option: hello
                """);

        assertThat(toUri()).startsWith("file:target/data/ParametersMultiValueTest/out?")
                .contains("autoCreate=false").contains("scheduler.my-option=hello");
        FileEndpoint file = context.getEndpoint(toUri(), FileEndpoint.class);
        assertThat(file.isAutoCreate()).isFalse();
        assertThat(file.getSchedulerProperties()).containsEntry("my-option", "hello");
    }

    @Test
    void consumer() throws Exception {
        loadRoutes("""
                - from:
                    uri: file:target/data/ParametersMultiValueTest/in
                    parameters:
                      initial-delay: 0
                      scheduler.pool-size: 2
                    steps:
                      - to: mock:result
                """);

        assertThat(context.getRouteDefinitions().get(0).getInput().getEndpointUri())
                .isEqualTo("file:target/data/ParametersMultiValueTest/in?initialDelay=0&scheduler.pool-size=2");

        // a scheduler property is set on the scheduler bean, which takes a property name in dash form as well
        context.start();
        FileConsumer consumer = (FileConsumer) context.getRoutes().get(0).getConsumer();
        DefaultScheduledPollConsumerScheduler scheduler = (DefaultScheduledPollConsumerScheduler) consumer.getScheduler();
        assertThat(scheduler.getPoolSize()).isEqualTo(2);
    }

    private String toUri() {
        return ((ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0)).getEndpointUri();
    }
}
