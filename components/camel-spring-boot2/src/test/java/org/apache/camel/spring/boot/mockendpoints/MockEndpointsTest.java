/**
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
package org.apache.camel.spring.boot.mockendpoints;

import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(CamelSpringBootRunner.class)
@MockEndpoints
@SpringBootApplication
@SpringBootTest(classes = MockEndpointsTest.class)
public class MockEndpointsTest {

    @Autowired
    FluentProducerTemplate producerTemplate;

    @Autowired
    CamelContext camelContext;

    @Test
    public void shouldMockEndpoints() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock://seda:foo", MockEndpoint.class);

        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        producerTemplate.withBody(msg).to("direct:start").send();

        // Then
        mock.assertIsSatisfied();
    }

}
