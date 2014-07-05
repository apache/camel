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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class RecipientListWithDelimiterTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testRecipientList() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").recipientList(header("myHeader"), "#");
            }
        });
        context.start();

        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        sendBody();

        assertMockEndpointsSatisfied();
    }

    public void testRecipientListWithDelimiterDisabled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").recipientList(header("myHeader"), "false");
            }
        });
        context.start();

        MockEndpoint xyz = getMockEndpoint("mock:falseDelimiterTest");
        xyz.expectedBodiesReceived("answer");

        template.sendBodyAndHeader("direct:a", "answer", "myHeader", "mock:falseDelimiterTest");

        assertMockEndpointsSatisfied();
    }

    public void testRecipientListWithTokenizer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").recipientList(header("myHeader").tokenize("#"));
            }
        });
        context.start();

        MockEndpoint x = getMockEndpoint("mock:x");
        MockEndpoint y = getMockEndpoint("mock:y");
        MockEndpoint z = getMockEndpoint("mock:z");

        x.expectedBodiesReceived("answer");
        y.expectedBodiesReceived("answer");
        z.expectedBodiesReceived("answer");

        sendBody();

        assertMockEndpointsSatisfied();
    }

    protected void sendBody() {
        template.sendBodyAndHeader("direct:a", "answer", "myHeader", "mock:x#mock:y#mock:z");
    }

}