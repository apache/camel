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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyException;
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.PropertiesHelper.asProperties;
import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigurationTest extends YamlTestSupport {

    @Test
    void routeConfiguration() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                    - routeConfiguration:
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Sorry"
                                - to: "mock:on-exception"
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyException.class.getName()));

        withMock("mock:on-exception", mock -> mock.expectedBodiesReceived("Sorry"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationOnCompletion() throws Exception {
        loadRoutes("""
                    - routeConfiguration:
                        onCompletion:
                          - onCompletion:
                              steps:
                                - transform:
                                    constant: "Completed"
                                - to: "mock:on-completion"
                    - from:
                        uri: "direct:start"
                        steps:
                          - log: "hello"
                """);

        withMock("mock:on-completion", mock -> mock.expectedBodiesReceived("Completed"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationPrecondition() throws Exception {
        context.getPropertiesComponent().setInitialProperties(asProperties("activate", "true"));
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                    - routeConfiguration:
                        precondition: "{{!activate}}"
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Not Activated"
                                - to: "mock:on-exception"
                    - routeConfiguration:
                        precondition: "{{activate}}"
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Activated"
                                - to: "mock:on-exception"
                        onCompletion:
                          - onCompletion:
                              steps:
                                - transform:
                                    constant: "Completed"
                                - to: "mock:on-completion"
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyException.class.getName(),
                MyException.class.getName()));

        withMock("mock:on-exception", mock -> mock.expectedBodiesReceived("Activated"));
        withMock("mock:on-completion", mock -> mock.expectedBodiesReceived("Completed"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
        assertThat(context.getRouteConfigurationDefinitions().size()).isEqualTo(1);

        RouteConfigurationDefinition config = context.getRouteConfigurationDefinitions().get(0);
        assertThat(config.getPrecondition()).isEqualTo("{{activate}}");
    }

    @Test
    void routeConfigurationSeparate() throws Exception {
        // global configurations
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                    - routeConfiguration:
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Sorry"
                                - to: "mock:on-exception"
                """.formatted(MyFailingProcessor.class.getName(), MyException.class.getName()));

        // routes
        loadRoutes("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                    - from:
                        uri: "direct:start2"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyFailingProcessor.class.getName()));

        withMock("mock:on-exception", mock -> mock.expectedBodiesReceived("Sorry", "Sorry"));

        context.start();

        withTemplate(t -> {
            t.to("direct:start").withBody("hello").send();
            t.to("direct:start2").withBody("hello2").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationId() throws Exception {
        // global configurations
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                    - routeConfiguration:
                        id: handleError
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Sorry"
                                - to: "mock:on-exception"
                """.formatted(MyFailingProcessor.class.getName(), MyException.class.getName()));

        // routes
        loadRoutes("""
                    - route:
                        routeConfigurationId: handleError
                        from:
                          uri: "direct:start"
                          steps:
                            - process:
                                ref: "myFailingProcessor"
                    - route:
                        from:
                          uri: "direct:start2"
                          steps:
                            - process:
                                ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyFailingProcessor.class.getName()));

        withMock("mock:on-exception", mock -> mock.expectedBodiesReceived("Sorry"));

        context.start();

        AtomicReference<Exchange> out1 = new AtomicReference<>();
        AtomicReference<Exchange> out2 = new AtomicReference<>();
        withTemplate(t -> {
            out1.set(t.to("direct:start").withBody("hello").send());
            out2.set(t.to("direct:start2").withBody("hello2").send());
        });

        MockEndpoint.assertIsSatisfied(context);

        assertThat(out1.get().isFailed()).isFalse();
        assertThat(out2.get().isFailed()).isTrue();
    }

    @Test
    void routeConfigurationErrorHandler() throws Exception {
        // global configurations
        loadRoutes("""
                    - beans:
                      - name: myFailingProcessor
                        type: %s
                    - routeConfiguration:
                        errorHandler:
                          deadLetterChannel:
                            deadLetterUri: "mock:on-error"
                """.formatted(MyFailingProcessor.class.getName()));

        // routes
        loadRoutes("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                    - from:
                        uri: "direct:start2"
                        steps:
                          - process:
                              ref: "myFailingProcessor"
                """.formatted(MyFailingProcessor.class.getName(), MyFailingProcessor.class.getName()));

        withMock("mock:on-error", mock -> mock.expectedBodiesReceived("hello", "hello2"));

        context.start();

        withTemplate(t -> {
            t.to("direct:start").withBody("hello").send();
            t.to("direct:start2").withBody("hello2").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationIntercept() throws Exception {
        loadRoutesNoValidate("""
                    - routeConfiguration:
                        intercept:
                          - intercept:
                              steps:
                                - transform:
                                    constant: "intercepted"
                                - to: "mock:intercepted"
                    - from:
                        uri: "direct:start"
                        steps:
                          - log: "hello"
                """);

        withMock("mock:intercepted", mock -> mock.expectedBodiesReceived("intercepted"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationInterceptFrom() throws Exception {
        loadRoutesNoValidate("""
                    - routeConfiguration:
                        interceptFrom:
                          - interceptFrom:
                              uri: "direct:start"
                              steps:
                                - transform:
                                    constant: "intercepted"
                                - to: "mock:intercepted"
                    - from:
                        uri: "direct:start"
                        steps:
                          - log: "hello"
                """);

        withMock("mock:intercepted", mock -> mock.expectedBodiesReceived("intercepted"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationInterceptSendToEndpoint() throws Exception {
        loadRoutesNoValidate("""
                    - routeConfiguration:
                        interceptSendToEndpoint:
                          - interceptSendToEndpoint:
                              uri: "direct:start"
                              steps:
                                - transform:
                                    constant: "intercepted"
                                - to: "mock:intercepted"
                    - from:
                        uri: "direct:start"
                        steps:
                          - log: "hello"
                """);

        withMock("mock:intercepted", mock -> mock.expectedBodiesReceived("intercepted"));

        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void routeConfigurationNote() throws Exception {
        loadRoutesNoValidate("""
                    - routeConfiguration:
                        id: myConfig
                        description: my route config
                        note: a developer note
                        onException:
                          - onException:
                              handled:
                                constant: "true"
                              exception:
                                - %s
                              steps:
                                - transform:
                                    constant: "Sorry"
                                - to: "mock:on-exception"
                """.formatted(MyException.class.getName()));

        assertThat(context.getRouteConfigurationDefinitions().size()).isEqualTo(1);

        RouteConfigurationDefinition config = context.getRouteConfigurationDefinitions().get(0);
        assertThat(config.getId()).isEqualTo("myConfig");
        assertThat(config.getDescription()).isEqualTo("my route config");
        assertThat(config.getNote()).isEqualTo("a developer note");
    }

    @Test
    void routeConfigurationHasLineNumber() throws Exception {
        Resource res = asResource("route-config-line",
                """
                            - routeConfiguration:
                                id: myConfig
                                onException:
                                  - onException:
                                      handled:
                                        constant: "true"
                                      exception:
                                        - java.lang.Exception
                                      steps:
                                        - to: "mock:on-exception"
                        """);
        PluginHelper.getRoutesLoader(context).loadRoutes(res);

        assertThat(context.getRouteConfigurationDefinitions().size()).isEqualTo(1);

        RouteConfigurationDefinition config = context.getRouteConfigurationDefinitions().get(0);
        assertThat(config.getId()).isEqualTo("myConfig");
        assertThat(config.getLineNumber() >= 0).isTrue();
        assertThat(config.getLocation()).isNotNull();
    }
}
