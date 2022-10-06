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
package org.apache.camel.component.jms.issues;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test based on user on user forum with an issue.
 */
public class JmsMessageHeaderContentBasedRouterTest extends AbstractJMSTest {

    @Test
    public void testCBR() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody("activemq:queue:JmsInOutRoutingSlipTest.start", "Hello World");

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
                Predicate isA = header("route").isEqualTo("a");
                Predicate isB = header("route").isEqualTo("b");

                from("activemq:queue:JmsInOutRoutingSlipTest.start")
                        .bean(MyPreProcessorBean.class, "determineRouting")
                        .choice()
                        .when(isA).to("mock:a")
                        .when(isB).to("mock:b")
                        .end();

            }
        };
    }

    public static class MyPreProcessorBean {

        public void determineRouting(Exchange exchange) {
            exchange.getIn().setHeader("route", "b");
        }
    }

}
