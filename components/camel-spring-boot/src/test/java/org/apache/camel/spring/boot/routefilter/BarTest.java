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
package org.apache.camel.spring.boot.routefilter;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(CamelSpringBootRunner.class)
@SpringBootApplication
@SpringBootTest(classes = BarTest.class,
    properties = {"camel.springboot.java-routes-include-pattern=**/Bar*"})
public class BarTest {

    @Autowired
    ProducerTemplate producerTemplate;

    @Autowired
    ModelCamelContext camelContext;

    @Test
    public void shouldSendToBar() throws Exception {
        // Given
        MockEndpoint mock = camelContext.getEndpoint("mock:bar", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello Bar");

        // When
        producerTemplate.sendBody("direct:start", "Hello Bar");

        // Then
        mock.assertIsSatisfied();
    }

}
