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
package org.apache.camel.component.jms;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JmsToDTest extends AbstractJMSTest {

    @Test
    public void testToD() throws Exception {
        getMockEndpoint("mock:JmsToDTest.bar").expectedBodiesReceived("Hello bar");
        getMockEndpoint("mock:JmsToDTest.beer").expectedBodiesReceived("Hello beer");

        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsToDTest.bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsToDTest.beer");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD
                from("direct:start").toD("activemq:queue:${header.where}");

                from("activemq:queue:JmsToDTest.bar").to("mock:JmsToDTest.bar");
                from("activemq:queue:JmsToDTest.beer").to("mock:JmsToDTest.beer");
            }
        };
    }
}
