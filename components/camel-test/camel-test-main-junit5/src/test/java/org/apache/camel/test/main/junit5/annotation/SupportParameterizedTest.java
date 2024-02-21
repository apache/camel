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
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.common.MyMainClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test class ensuring that a parameterized test is supported.
 */
@CamelMainTest(mainClass = MyMainClass.class)
class SupportParameterizedTest {

    @EndpointInject("mock:echo")
    MockEndpoint mock;

    @EndpointInject("direct:echo")
    ProducerTemplate template;

    @AfterEach
    void reset() {
        mock.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = { "hello", "parameterized", "test" })
    void shouldSupportMultipleCalls(String value) throws Exception {
        mock.expectedBodiesReceived(value);
        String result = template.requestBody((Object) value, String.class);
        mock.assertIsSatisfied();
        assertEquals(value, result);
    }

    @Nested
    class NestedTest {

        @ParameterizedTest
        @ValueSource(strings = { "hello", "nested", "test" })
        void shouldSupportNestedTest(String value) throws Exception {
            shouldSupportMultipleCalls(value);
        }
    }
}
