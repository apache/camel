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

import jakarta.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CamelCdiExtension.class)
class CamelCdiAutoDetectRoutesTest {

    // Declare a RouteBuilder bean for testing purpose
    // that is automatically added to the Camel context
    static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:out").routeId("test").to("mock:out");
        }

    }

    // Retrieve the MockEndpoint for further assertions
    @Inject
    @Uri("mock:out")
    MockEndpoint mock;
    // And finally retrieve the ProducerTemplate to send messages
    @Inject
    @Uri("direct:out")
    ProducerTemplate template;

    @Test
    void testSendMessage() throws Exception {
        String expectedBody = "Camel Rocks";

        mock.expectedBodiesReceived(expectedBody);

        template.sendBody(expectedBody);

        mock.assertIsSatisfied();
    }
}
