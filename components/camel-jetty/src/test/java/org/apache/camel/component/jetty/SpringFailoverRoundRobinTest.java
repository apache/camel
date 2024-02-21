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
package org.apache.camel.component.jetty;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpringFailoverRoundRobinTest extends CamelSpringTestSupport {

    private static int port1 = AvailablePortFinder.getNextAvailable();
    private static int port2 = AvailablePortFinder.getNextAvailable();
    private static int port3 = AvailablePortFinder.getNextAvailable();
    private static int port4 = AvailablePortFinder.getNextAvailable();

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("JettySpringFailoverRoundRobinTest.port1", Integer.toString(port1));
        System.setProperty("JettySpringFailoverRoundRobinTest.port2", Integer.toString(port2));
        System.setProperty("JettySpringFailoverRoundRobinTest.port3", Integer.toString(port3));
        System.setProperty("JettySpringFailoverRoundRobinTest.port4", Integer.toString(port4));
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/jetty/JettySpringFailoverRoundRobinTest.xml");
    }

    @Test
    void testJettySpringFailoverRoundRobin() throws Exception {
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestBad").expectedMessageCount(1);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestBad2").expectedMessageCount(1);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestGood").expectedMessageCount(1);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestGood2").expectedMessageCount(0);

        String reply = template.requestBody("direct:JettySpringFailoverRoundRobinTestStart", null, String.class);
        assertEquals("Good", reply);

        MockEndpoint.assertIsSatisfied(context);

        // reset mocks and send a message again to see that round robin
        // continue where it should
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestBad").expectedMessageCount(0);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestBad2").expectedMessageCount(0);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestGood").expectedMessageCount(0);
        getMockEndpoint("mock:JettySpringFailoverRoundRobinTestGood2").expectedMessageCount(1);

        reply = template.requestBody("direct:JettySpringFailoverRoundRobinTestStart", null, String.class);
        assertEquals("Also good", reply);
    }

}
