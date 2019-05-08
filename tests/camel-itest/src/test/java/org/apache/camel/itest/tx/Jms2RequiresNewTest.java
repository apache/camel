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
package org.apache.camel.itest.tx;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.ITestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Unit test will look for the spring .xml file with the same class name
 * but postfixed with -config.xml as filename.
 * <p/>
 * We use Spring Testing for unit test, eg we extend AbstractJUnit4SpringContextTests
 * that is a Spring class.
 */
@ContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class Jms2RequiresNewTest extends AbstractJUnit4SpringContextTests {

    private static final int PORT3 = ITestSupport.getPort3();
    @Autowired
    private CamelContext camelContext;

    @EndpointInject("mock:result1")
    private MockEndpoint result1;

    @EndpointInject("mock:result2")
    private MockEndpoint result2;

    @EndpointInject("mock:dlq")
    private MockEndpoint dlq;

    @EndpointInject("direct:start")
    private ProducerTemplate start;

    @Before
    public void setUpRoute() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .markRollbackOnly();

                from("direct:start").transacted("PROPAGATION_REQUIRES_NEW").to("activemq:queue:start");
                from("activemq:queue:result1").transacted("PROPAGATION_REQUIRES_NEW").to("mock:result1");
                from("activemq:queue:result2").transacted("PROPAGATION_REQUIRES_NEW").to("mock:result2");
                from("activemq:queue:ActiveMQ.DLQ").transacted("PROPAGATION_REQUIRES_NEW").to("mock:dlq");

                from("activemq:queue:start")
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .setExchangePattern(ExchangePattern.InOnly)
                        .to("activemq:queue:result1")
                        .to("direct:route2")
                        .choice()
                            .when(body().contains("Neverland"))
                                .throwException(new RuntimeException("Expected!"));

                from("direct:route2")
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .setExchangePattern(ExchangePattern.InOnly)
                        .to("activemq:queue:result2");

            }
        });
    }

    @Test
    public void testSendThrowingException() throws Exception {
        result1.expectedMessageCount(0);
        result2.expectedMessageCount(1);
        dlq.expectedMessageCount(1);

        start.sendBody("Single ticket to Neverland please!");

        result2.assertIsSatisfied();
        dlq.assertIsSatisfied();
        result1.assertIsSatisfied();
    }

    @Test
    public void testSend() throws Exception {
        result1.expectedMessageCount(1);
        result2.expectedMessageCount(1);
        dlq.expectedMessageCount(0);

        start.sendBody("Piotr Klimczak");

        result1.assertIsSatisfied();
        result2.assertIsSatisfied();
        dlq.assertIsSatisfied();
    }

}
