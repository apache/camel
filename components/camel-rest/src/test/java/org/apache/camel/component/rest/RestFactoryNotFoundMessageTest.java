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
package org.apache.camel.component.rest;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When the Rest DSL finds no HTTP component to use, the message says which components it looks for and the artifact of
 * each; a component name that cannot be found says what to add or what the option takes. This module has no HTTP
 * component on its test classpath.
 */
class RestFactoryNotFoundMessageTest {

    private SimpleRegistry registry;
    private CamelContext camelContext;

    @BeforeEach
    void setUp() {
        registry = new SimpleRegistry();
        camelContext = new DefaultCamelContext(registry);
        RestComponent component = new RestComponent();
        component.setCamelContext(camelContext);
        camelContext.addComponent("rest", component);
        RestApiComponent apiComponent = new RestApiComponent();
        apiComponent.setCamelContext(camelContext);
        camelContext.addComponent("rest-api", apiComponent);
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    void consumerWithoutFactorySaysWhichComponentsToAdd() {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Cannot find RestConsumerFactory in Registry or as a Component to use")
                .hasMessageContaining("platform-http (camel-platform-http), servlet (camel-servlet), jetty (camel-jetty)")
                .hasMessageContaining("set the component option of the rest configuration");
    }

    @Test
    void producerWithoutFactorySaysWhichComponentsToAdd() {
        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        assertThatThrownBy(endpoint::createProducer)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Cannot find RestProducerFactory in Registry or as a Component to use")
                .hasMessageContaining("http (camel-http), vertx-http (camel-vertx-http)")
                .hasMessageContaining("set the producerComponent option of the rest configuration");
    }

    @Test
    void apiConsumerWithoutFactorySaysWhichComponentsToAdd() {
        RestApiEndpoint endpoint = (RestApiEndpoint) camelContext.getEndpoint("rest-api:api-doc");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Cannot find RestApiConsumerFactory in Registry or as a Component to use")
                .hasMessageContaining("platform-http (camel-platform-http), servlet (camel-servlet)");
    }

    @Test
    void unknownComponentNameSaysWhatTheOptionTakes() {
        // such as the name of a servlet given as the component
        RestEndpoint endpoint = (RestEndpoint) camelContext
                .getEndpoint("rest:get:users?host=localhost&consumerComponentName=MyServlet");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isExactlyInstanceOf(NoSuchBeanException.class)
                .hasMessageStartingWith("No bean could be found in the registry for: MyServlet of type: "
                                        + RestConsumerFactory.class.getName())
                .hasMessageContaining("did you mean 'servlet'?")
                .hasMessageContaining("takes the name of a component that supports the Rest DSL: platform-http, servlet");
    }

    @Test
    void missingConsumerComponentSaysWhichArtifactToAdd() {
        RestEndpoint endpoint = (RestEndpoint) camelContext
                .getEndpoint("rest:get:users?host=localhost&consumerComponentName=servlet");

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        }))
                .isExactlyInstanceOf(NoSuchBeanException.class)
                .hasMessageStartingWith("No bean could be found in the registry for: servlet of type: "
                                        + RestConsumerFactory.class.getName())
                .hasMessageContaining("add camel-servlet to the classpath")
                .hasMessageNotContaining("takes the name of a component");
    }

    @Test
    void missingProducerComponentSaysWhichArtifactToAdd() {
        RestEndpoint endpoint = (RestEndpoint) camelContext
                .getEndpoint("rest:get:users?host=localhost&producerComponentName=vertx-http");

        assertThatThrownBy(endpoint::createProducer)
                .isExactlyInstanceOf(NoSuchBeanException.class)
                .hasMessageStartingWith("No bean could be found in the registry for: vertx-http of type: "
                                        + RestProducerFactory.class.getName())
                .hasMessageContaining("add camel-vertx-http to the classpath");
    }

    @Test
    void unknownProducerComponentNameSaysWhatTheOptionTakes() {
        RestEndpoint endpoint = (RestEndpoint) camelContext
                .getEndpoint("rest:get:users?host=localhost&producerComponentName=myClient");

        assertThatThrownBy(endpoint::createProducer)
                .isExactlyInstanceOf(NoSuchBeanException.class)
                .hasMessageStartingWith("No bean could be found in the registry for: myClient of type: "
                                        + RestProducerFactory.class.getName())
                .hasMessageContaining("takes the name of a component that supports the Rest DSL: http, vertx-http");
    }

    @Test
    void registeredFactoryIsUsed() throws Exception {
        RestConsumerFactory factory = mock(RestConsumerFactory.class);
        Consumer consumer = mock(Consumer.class);
        when(factory.createConsumer(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(consumer);
        registry.bind("myFactory", factory);

        RestEndpoint endpoint = (RestEndpoint) camelContext.getEndpoint("rest:get:users?host=localhost");

        assertThat(endpoint.createConsumer(exchange -> {
        })).isSameAs(consumer);
    }
}
