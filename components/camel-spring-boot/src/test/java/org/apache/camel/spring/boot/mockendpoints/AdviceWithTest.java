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

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.Assert.assertFalse;

@RunWith(CamelSpringBootRunner.class)
@UseAdviceWith
@SpringBootApplication
@SpringBootTest(classes = AdviceWithTest.class)
public class AdviceWithTest {

    @Autowired
    ProducerTemplate producerTemplate;

    @Autowired
    ModelCamelContext camelContext;

    @Test
    public void shouldMockEndpoints() throws Exception {
        // context should not be started because we enabled @UseAdviceWith
        assertFalse(camelContext.getStatus().isStarted());

        camelContext.getRouteDefinitions().get(0).adviceWith(camelContext, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("seda:start");
                weaveAddLast().to("mock:result");
            }
        });

        // manual start camel
        camelContext.start();

        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);

        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        producerTemplate.sendBody("seda:start", msg);

        // Then
        mock.assertIsSatisfied();
    }

}
