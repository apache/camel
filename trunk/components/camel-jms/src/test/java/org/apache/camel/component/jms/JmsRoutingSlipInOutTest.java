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
package org.apache.camel.component.jms;

import java.util.Map;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class JmsRoutingSlipInOutTest extends CamelTestSupport {

    @Test
    public void testInOutRoutingSlip() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Result-Done-B-A-Hello");

        template.sendBody("activemq:queue:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:start")
                    .to("direct:start")
                    .to("bean:myBean?method=doResult")
                    .to("mock:result");

                from("direct:start")
                    .to("bean:myBean?method=createSlip")
                    .setExchangePattern(ExchangePattern.InOut)
                    .routingSlip(header("mySlip"))
                    .to("bean:myBean?method=backFromSlip");

                from("activemq:queue:a")
                    .to("bean:myBean?method=doA");

                from("activemq:queue:b")
                    .to("bean:myBean?method=doB");
            }
        };
    }

    public static final class MyBean {

        public void createSlip(@Headers Map<String, Object> headers) {
            headers.put("mySlip", "activemq:queue:a,activemq:queue:b");
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
