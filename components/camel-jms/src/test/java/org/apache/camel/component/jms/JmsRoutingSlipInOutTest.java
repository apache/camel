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

import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;

/**
 *
 */
public class JmsRoutingSlipInOutTest extends AbstractJMSTest {

    @BindToRegistry("myBean")
    private final MyBean bean = new MyBean();

    @Test
    public void testInOutRoutingSlip() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Result-Done-B-A-Hello");

        template.sendBody("activemq:queue:JmsRoutingSlipInOutTest.start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsRoutingSlipInOutTest.start").to("direct:start").to("bean:myBean?method=doResult")
                        .to("mock:result");

                from("direct:start").to("bean:myBean?method=createSlip").setExchangePattern(ExchangePattern.InOut)
                        .routingSlip(header("mySlip"))
                        .to("bean:myBean?method=backFromSlip");

                from("activemq:queue:JmsRoutingSlipInOutTest.a").to("bean:myBean?method=doA");

                from("activemq:queue:JmsRoutingSlipInOutTest.b").to("bean:myBean?method=doB");
            }
        };
    }

    public static final class MyBean {

        public void createSlip(@Headers Map<String, Object> headers) {
            headers.put("mySlip", "activemq:queue:JmsRoutingSlipInOutTest.a,activemq:queue:JmsRoutingSlipInOutTest.b");
        }

        public String backFromSlip(String body) {
            return "Done-" + body;
        }

        public String doA(String body) {
            return "A-" + body;
        }

        public String doB(String body) {
            return "B-" + body;
        }

        public String doResult(String body) {
            return "Result-" + body;
        }
    }
}
