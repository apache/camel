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
package org.apache.camel.itest.greeter;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MulticastCxfTest extends CamelSpringTestSupport {
    private static int port = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("MulticastCxfTest.port", Integer.toString(port));
    }

    @EndpointInject("mock:reply")
    protected MockEndpoint replyEndpoint;

    @EndpointInject("mock:reply2")
    protected MockEndpoint reply2Endpoint;

    @EndpointInject("mock:output")
    protected MockEndpoint outputEndpoint;
    
    

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/greeter/MulticastCxfTest-context.xml");
    }

    @Test
    public void testMulticastCXF() throws Exception {
        replyEndpoint.expectedBodiesReceived("Hello Willem", "Hello Claus", "Hello Jonathan");
        reply2Endpoint.expectedBodiesReceived("Bye Willem", "Bye Claus", "Bye Jonathan");
        outputEndpoint.expectedBodiesReceived("Bye Willem", "Bye Claus", "Bye Jonathan");

        // returns the last message from the recipient list
        String out = template.requestBodyAndHeader("direct:start", "Willem", CxfConstants.OPERATION_NAME, "greetMe", String.class);
        assertEquals("Bye Willem", out);

        // call again to ensure that works also
        // returns the last message from the recipient list
        String out2 = template.requestBodyAndHeader("direct:start", "Claus", CxfConstants.OPERATION_NAME, "greetMe", String.class);
        assertEquals("Bye Claus", out2);

        // and call again to ensure that it really works also
        // returns the last message from the recipient list
        String out3 = template.requestBodyAndHeader("direct:start", "Jonathan", CxfConstants.OPERATION_NAME, "greetMe", String.class);
        assertEquals("Bye Jonathan", out3);

        replyEndpoint.assertIsSatisfied();
        reply2Endpoint.assertIsSatisfied();
        outputEndpoint.assertIsSatisfied();
    }
}
