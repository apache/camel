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
package org.apache.camel.test.cdi;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(CamelCdiExtension.class)
class CamelCdiParameterizedTest {

    @Inject
    @Uri("mock:out")
    MockEndpoint mock;

    @Inject
    @Uri("direct:in")
    ProducerTemplate producer;

    @ParameterizedTest
    @ValueSource(strings = { "hello", "parameterized", "test" })
    void test(String value) throws Exception {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(value);

        producer.sendBody(value);

        MockEndpoint.assertIsSatisfied(1L, TimeUnit.SECONDS, mock);
    }

    @AfterEach
    public void before() {
        mock.reset();
    }

    static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:in").routeId("test").to("mock:out");
        }
    }
}
