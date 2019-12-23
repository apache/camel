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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RecipientListCxf2Test extends CamelSpringTestSupport {
    
    private static int port1 = AvailablePortFinder.getNextAvailable();
    private static int port2 = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("RecipientListCxf2Test.port1", Integer.toString(port1));
        System.setProperty("RecipientListCxf2Test.port2", Integer.toString(port2));
    }

    @EndpointInject("mock:reply")
    protected MockEndpoint replyEndpoint;

    @EndpointInject("mock:reply2")
    protected MockEndpoint reply2Endpoint;

    @EndpointInject("mock:output")
    protected MockEndpoint outputEndpoint;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/greeter/RecipientListCxf2Test-context.xml");
    }
    
    @Test
    public void testRecipientListCXF2() throws Exception {
        replyEndpoint.expectedBodiesReceived("Hello Willem", "Hello Jonathan", "Hello Freeman");
        reply2Endpoint.expectedBodiesReceived("Bye Claus", "Bye Jonathan", "Bye Freeman");
        outputEndpoint.expectedBodiesReceived("Hello Willem", "Bye Claus", "Bye Jonathan", "Hello Freeman");

        Map<String, Object> headers = new HashMap<>();
        headers.put(CxfConstants.OPERATION_NAME, "greetMe");
        headers.put("foo", "cxf:bean:clientEndpoint?address=http://localhost:" + port1 + "/SoapContext/SoapPort");

        // returns the last message from the recipient list
        Object out = template.requestBodyAndHeaders("direct:start", "Willem", headers, String.class);
        assertEquals("Hello Willem", out);

        // change foo headers
        headers.put("foo", "cxf:bean:clientEndpoint?address=http://localhost:" + port2 + "/SoapContext/SoapPort");

        // call again to ensure that works also
        // returns the last message from the recipient list
        String out2 = template.requestBodyAndHeaders("direct:start", "Claus", headers, String.class);
        assertEquals("Bye Claus", out2);

        // change foo headers again
        headers.put("foo", "cxf:bean:clientEndpoint?address=http://localhost:" + port1 + "/SoapContext/SoapPort"
                + ",cxf:bean:clientEndpoint?address=http://localhost:" + port2 + "/SoapContext/SoapPort");

        // and call again to ensure that it really works also
        // returns the last message from the recipient list
        String out3 = template.requestBodyAndHeaders("direct:start", "Jonathan", headers, String.class);
        assertEquals("Bye Jonathan", out3);

        // change foo headers again
        headers.put("foo", "cxf:bean:clientEndpoint?address=http://localhost:" + port2 + "/SoapContext/SoapPort"
                + ",cxf:bean:clientEndpoint?address=http://localhost:" + port1 + "/SoapContext/SoapPort");

        // and call again to ensure that it really works also
        // returns the last message from the recipient list
        String out4 = template.requestBodyAndHeaders("direct:start", "Freeman", headers, String.class);
        assertEquals("Hello Freeman", out4);

        replyEndpoint.assertIsSatisfied();
        reply2Endpoint.assertIsSatisfied();
        outputEndpoint.assertIsSatisfied();
    }
}
