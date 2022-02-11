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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Beans(classes = ExternalTestBuilder.class)
@ExtendWith(CamelCdiExtension.class)
class CamelCdiAdviceExternalTest {

    @EndpointInject("mock:test")
    MockEndpoint mock;

    @Produce("direct:in")
    ProducerTemplate template;

    @Test
    void testSendMessage(ModelCamelContext context) throws Exception {
        assertNotNull(context);
        String expectedBody = "Camel Rocks";

        mock.expectedBodiesReceived(expectedBody);

        template.sendBody(expectedBody);

        mock.assertIsSatisfied();
    }

    static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:in").routeId("route").to("direct:out");
        }

    }
}
