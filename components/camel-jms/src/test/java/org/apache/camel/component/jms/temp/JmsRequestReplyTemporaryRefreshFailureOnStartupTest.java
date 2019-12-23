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
package org.apache.camel.component.jms.temp;

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsRequestReplyTemporaryRefreshFailureOnStartupTest extends CamelTestSupport {

    private String brokerName;
    private Long recoveryInterval = 1000L;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        brokerName = "test-broker-" + System.currentTimeMillis();
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://" + brokerName + "?create=false");
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .inOut("jms:queue:foo?recoveryInterval=" + recoveryInterval)
                        .to("mock:result");

                from("jms:queue:foo")
                        .setBody(simple("pong"));
            }
        };
    }

    @Test
    public void testTemporaryRefreshFailureOnStartup() throws Exception {
        //the first message will fail
        //the second message must be handled
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        //the first request will return with an error
        //because the broker is not started yet
        try {
            template.requestBody("direct:start", "ping");
        } catch (Exception exception) {

        }
        //wait for connection recovery before starting the broker
        Thread.sleep(recoveryInterval + 500L);
        String brokerUri = "vm://" + brokerName;
        BrokerService broker = new BrokerService();
        broker.setBrokerName(brokerName);
        broker.setBrokerId(brokerName);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(brokerUri);
        broker.start();
        //wait for the next recovery attempt
        Thread.sleep(recoveryInterval + 500L);
        template.asyncRequestBody("direct:start", "ping");

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
        broker.stop();
    }

}
