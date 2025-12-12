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
package org.apache.camel.component.jms.integration.spring.tx;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public final class JmsToJmsTransactedIT extends CamelSpringTestSupport {

    @Order(0)
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    /**
     * Used by spring xml configurations
     *
     * @return
     */
    public static String getServiceAddress() {
        return service.serviceAddress();
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/integration/spring/tx/JmsToJmsTransactedIT.xml");
    }

    @Order(1)
    @Test
    public void testJmsToJmsTestOK() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsToJmsTransactedIT")
                        .transacted()
                        .to("activemq:queue:JmsToJmsTransactedIT.reply");
            }
        });
        context.start();

        template.sendBody("activemq:queue:JmsToJmsTransactedIT", "Hello World");

        String reply = consumer.receiveBody("activemq:queue:JmsToJmsTransactedIT.reply", 5000, String.class);
        assertEquals("Hello World", reply);
    }

    @Order(2)
    @Test
    public void testJmsToJmsTestRollbackDueToException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsToJmsTransactedIT")
                        .transacted()
                        .to("mock:start")
                        .to("activemq:queue:JmsToJmsTransactedIT.reply")
                        .throwException(new IllegalArgumentException("Damn"));

                from("activemq:queue:JmsToJmsTransactedIT.reply").to("log:bar").to("mock:bar");
            }
        });
        context.start();

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(0);

        MockEndpoint start = getMockEndpoint("mock:start");
        start.expectedMessageCount(7); // default number of redeliveries by AMQ is 6 so we get 6+1

        template.sendBody("activemq:queue:JmsToJmsTransactedIT", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Order(3)
    @Test
    public void testJmsToJmsTestRollbackDueToRollback() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsToJmsTransactedIT")
                        .transacted()
                        .to("mock:start")
                        .to("activemq:queue:JmsToJmsTransactedIT.reply")
                        .rollback();

                from("activemq:queue:JmsToJmsTransactedIT.reply").to("log:bar").to("mock:bar");
            }
        });
        context.start();

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(0);

        MockEndpoint start = getMockEndpoint("mock:start");
        start.expectedMessageCount(7); // default number of redeliveries by AMQ is 6 so we get 6+1

        template.sendBody("activemq:queue:JmsToJmsTransactedIT", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // it should be moved to DLQ in JMS broker
        Object body = consumer.receiveBody("activemq:queue:DLQ", 2000);
        assertEquals("Hello World", body);
    }
}
