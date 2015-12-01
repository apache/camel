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
package org.apache.camel.component.amqp;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.qpid.server.Broker;
import org.apache.qpid.server.BrokerOptions;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.amqp.AMQPComponent.amqpComponent;

public class AMQPRouteTest extends CamelTestSupport {
    protected MockEndpoint resultEndpoint;
    protected Broker broker;
    
    @Test
    public void testJmsRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";

        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);
        template.sendBodyAndHeader("amqp1-0:queue:ping", expectedBody, "cheese", 123);
        resultEndpoint.assertIsSatisfied();
    }


    @Before
    public void setUp() throws Exception {
        BrokerOptions options = new BrokerOptions();
        options.setConfigurationStoreType("memory");
        options.setInitialConfigurationLocation("src/test/resources/config.json");
        options.setLogConfigFileLocation("src/test/resources/log4j.xml");

        broker = new Broker();
        broker.startup(options);

        super.setUp();
        resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        broker.shutdown();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("amqp1-0", amqpComponent("amqp://guest:guest@localhost:5672?remote-host=test", false));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("amqp1-0:queue:ping")
                    .to("log:routing")
                    .to("mock:result");
            }
        };
    }

}
