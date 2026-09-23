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

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutesTest extends YamlTestSupport {

    @Test
    void loadFrom() throws Exception {
        loadRoutes("""
                    - from:
                        uri: "direct:info"
                        steps:
                          - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadFromDescription() throws Exception {
        loadRoutes("""
                    - from:
                        id: from-demo
                        description: from something cool
                        uri: "direct:info"
                        steps:
                          - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getId()).isEqualTo("from-demo");
        assertThat(route.getInput().getDescription()).isEqualTo("from something cool");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadFromWithParameters() throws Exception {
        loadRoutes("""
                    - from:
                        uri: "direct:info"
                        parameters:
                          timeout: 1234
                        steps:
                          - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info?timeout=1234");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadMultiFrom() throws Exception {
        loadRoutes("""
                    - from:
                        uri: "direct:1"
                        steps:
                          - log: "1"
                    - from:
                        uri: "direct:2"
                        steps:
                          - log: "2"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        RouteDefinition route1 = context.getRouteDefinitions().get(0);
        assertThat(route1.getInput().getEndpointUri()).isEqualTo("direct:1");
        assertThat(route1.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log1 = (LogDefinition) route1.getOutputs().get(0);
        assertThat(log1.getMessage()).isEqualTo("1");

        RouteDefinition route2 = context.getRouteDefinitions().get(1);
        assertThat(route2.getInput().getEndpointUri()).isEqualTo("direct:2");
        assertThat(route2.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log2 = (LogDefinition) route2.getOutputs().get(0);
        assertThat(log2.getMessage()).isEqualTo("2");
    }

    @Test
    void loadRoute() throws Exception {
        loadRoutes("""
                    - route:
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteWithParameters() throws Exception {
        loadRoutes("""
                    - route:
                        from:
                          uri: "direct:info"
                          parameters:
                            timeout: 1234
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info?timeout=1234");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteWithErrorHandlerRef() throws Exception {
        loadRoutes("""
                    - route:
                        errorHandlerRef: "myErrorHandler"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
        assertThat(route.isErrorHandlerFactorySet()).isTrue();
        assertThat(route.getErrorHandlerFactory().getClass()).isEqualTo(RefErrorHandlerDefinition.class);
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(RefErrorHandlerDefinition.class);
        RefErrorHandlerDefinition eh = (RefErrorHandlerDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getRef()).isEqualTo("myErrorHandler");
    }

    @Test
    void loadRouteWithErrorHandler() throws Exception {
        loadRoutes("""
                    - route:
                        errorHandler:
                          refErrorHandler:
                            ref: "myErrorHandler"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
        assertThat(route.isErrorHandlerFactorySet()).isTrue();
        assertThat(route.getErrorHandlerFactory().getClass()).isEqualTo(RefErrorHandlerDefinition.class);
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(RefErrorHandlerDefinition.class);
        RefErrorHandlerDefinition eh = (RefErrorHandlerDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getRef()).isEqualTo("myErrorHandler");
    }

    @Test
    void loadRouteWithErrorHandlerProperties() throws Exception {
        loadRoutes("""
                    - route:
                        errorHandler:
                          deadLetterChannel:
                            deadLetterUri: "mock:on-error"
                            redeliveryPolicy:
                              maximumRedeliveries: 3
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
        assertThat(route.isErrorHandlerFactorySet()).isTrue();
        assertThat(route.getErrorHandlerFactory().getClass()).isEqualTo(DeadLetterChannelDefinition.class);
        assertThat(route.getErrorHandlerFactory()).isInstanceOf(DeadLetterChannelDefinition.class);
        DeadLetterChannelDefinition eh = (DeadLetterChannelDefinition) route.getErrorHandlerFactory();
        assertThat(eh.getDeadLetterUri()).isEqualTo("mock:on-error");
        assertThat(eh.getRedeliveryPolicy().getMaximumRedeliveries()).isEqualTo("3");
    }

    @Test
    void loadRouteWithInputOutputTypes() throws Exception {
        loadRoutes("""
                    - route:
                        inputType:
                          urn: "plain/text"
                        outputType:
                          urn: "application/octet-stream"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInputType().getUrn()).isEqualTo("plain/text");
        assertThat(route.getOutputType().getUrn()).isEqualTo("application/octet-stream");

        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteInlinedCamelCase() throws Exception {
        loadRoutes("""
                    - route:
                        id: demo-route
                        streamCache: true
                        autoStartup: false
                        startupOrder: 123
                        routePolicyRef: "myPolicy"
                        shutdownRoute: "Defer"
                        shutdownRunningTask: "CompleteAllTasks"
                        delayer: "200"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("demo-route");
        assertThat(route.getStreamCache()).isEqualTo("true");
        assertThat(route.getAutoStartup()).isEqualTo("false");
        assertThat(route.getStartupOrder()).isEqualTo(123);
        assertThat(route.getRoutePolicyRef()).isEqualTo("myPolicy");
        assertThat(route.getShutdownRoute()).isEqualTo("Defer");
        assertThat(route.getShutdownRunningTask()).isEqualTo("CompleteAllTasks");
        assertThat(route.getDelayer()).isEqualTo("200");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteDescription() throws Exception {
        loadRoutes("""
                    - route:
                        id: demo-route
                        description: something cool
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("demo-route");
        assertThat(route.getDescription()).isEqualTo("something cool");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteDescriptionWithPrecondition() throws Exception {
        loadRoutes("""
                    - route:
                        id: demo-route
                        description: something cool
                        precondition: "{{?red}}"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("demo-route");
        assertThat(route.getDescription()).isEqualTo("something cool");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(route.getPrecondition()).isEqualTo("{{?red}}");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadFromNote() throws Exception {
        loadRoutes("""
                    - from:
                        id: from-demo
                        description: from something cool
                        note: a developer note
                        uri: "direct:info"
                        steps:
                          - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getId()).isEqualTo("from-demo");
        assertThat(route.getInput().getDescription()).isEqualTo("from something cool");
        assertThat(route.getInput().getNote()).isEqualTo("a developer note");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteWithFromDescription() throws Exception {
        loadRoutes("""
                    - route:
                        id: demo-route
                        description: something cool
                        from:
                          id: from-demo
                          description: from something cool
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("demo-route");
        assertThat(route.getDescription()).isEqualTo("something cool");

        assertThat(route.getInput().getId()).isEqualTo("from-demo");
        assertThat(route.getInput().getDescription()).isEqualTo("from something cool");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        assertThat(route.getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void loadRouteWithNodePrefixId() throws Exception {
        loadRoutes("""
                    - route:
                        id: foo
                        nodePrefixId: aaa
                        from:
                          uri: "direct:foo"
                          steps:
                            - to:
                                id: "myFoo"
                                uri: "mock:foo"
                            - to: "seda:foo"
                    - route:
                        id: bar
                        nodePrefixId: bbb
                        from:
                          uri: "direct:bar"
                          steps:
                            - to:
                                id: "myBar"
                                uri: "mock:bar"
                            - to: "seda:bar"
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);
        context.start();

        assertThat(context.getRoute("foo").filter("aaa*").size()).isEqualTo(2);
        assertThat(context.getRoute("bar").filter("bbb*").size()).isEqualTo(2);
    }
}
