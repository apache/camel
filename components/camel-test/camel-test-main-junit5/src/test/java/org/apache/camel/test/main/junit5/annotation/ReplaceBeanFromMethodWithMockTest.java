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
package org.apache.camel.test.main.junit5.annotation;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.Configure;
import org.apache.camel.test.main.junit5.ReplaceInRegistry;
import org.apache.camel.test.main.junit5.common.Greetings;
import org.apache.camel.test.main.junit5.common.MyConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * The test class ensuring that an existing bean can be replaced with a mock build manually in a method.
 */
@CamelMainTest
class ReplaceBeanFromMethodWithMockTest {

    @EndpointInject("mock:out")
    MockEndpoint mock;

    @EndpointInject("direct:in")
    ProducerTemplate template;

    @Configure
    protected void configure(MainConfigurationProperties configuration) {
        // Add the configuration class
        configuration.addConfiguration(MyConfiguration.class);
    }

    /**
     * Replace the default bean whose name is <i>myGreetings</i> and type is {@link Greetings} with this custom
     * implementation used for the test only.
     */
    @ReplaceInRegistry
    Greetings myGreetings() {
        final Greetings greetings = Mockito.mock(Greetings.class);
        when(greetings.sayHello()).thenReturn("Hola Will!");
        return greetings;
    }

    @Test
    void shouldReplaceTheBeanWithAMock() throws Exception {
        mock.expectedBodiesReceived("Hola Will!");
        String result = template.requestBody((Object) null, String.class);
        mock.assertIsSatisfied();
        assertEquals("Hola Will!", result);
    }
}
