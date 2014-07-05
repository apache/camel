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
package org.apache.camel.spring.issues;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AdviceWithTransactionIssueTest extends SpringTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/AdviceWithTransactionIssueTest.xml");
    }

    @Test
    public void testAdviceWithWeaveById() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("mock-b*").after().to("mock:last");
            }
        });
        context.start();

        MockEndpoint mockLast = getMockEndpoint("mock:last");
        mockLast.expectedBodiesReceived("bar");
        mockLast.setExpectedMessageCount(1);

        template.sendBody("seda:start", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithAddLast() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:last");
            }
        });
        context.start();

        MockEndpoint mockLast = getMockEndpoint("mock:last");
        mockLast.expectedBodiesReceived("bar");
        mockLast.setExpectedMessageCount(1);

        template.sendBody("seda:start", "bar");

        assertMockEndpointsSatisfied();
    }

}
