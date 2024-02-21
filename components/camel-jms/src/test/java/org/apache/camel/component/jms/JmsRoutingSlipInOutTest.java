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
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Headers;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(10)
public class JmsRoutingSlipInOutTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    @BindToRegistry("myBean")
    private final MyBean bean = new MyBean();

    @Test
    public void testInOutRoutingSlip() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Result-Done-B-A-Hello");

        template.sendBody("activemq:queue:JmsRoutingSlipInOutTest.start", "Hello");

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
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

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
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
