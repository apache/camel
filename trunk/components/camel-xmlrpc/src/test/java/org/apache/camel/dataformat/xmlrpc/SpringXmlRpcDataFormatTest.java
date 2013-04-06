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
package org.apache.camel.dataformat.xmlrpc;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmlrpc.XmlRpcRequestImpl;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.xmlrpc.XmlRpcRequest;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringXmlRpcDataFormatTest extends CamelSpringTestSupport {
    
    @Test
    public void testRequestMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:request");
        mock.expectedMessageCount(1);
        XmlRpcRequest result = template.requestBody("direct:request", new XmlRpcRequestImpl("greet", new Object[]{"you", 2}), XmlRpcRequest.class);
        assertNotNull(result);
        assertEquals("Get a wrong request operation name", "greet", result.getMethodName());
        assertEquals("Get a wrong request parameter size", 2, result.getParameterCount());
        assertEquals("Get a wrong request parameter", 2, result.getParameter(1));
        assertMockEndpointsSatisfied();
        
    }
    
    @Test
    public void testResponseMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:response");
        mock.expectedBodiesReceived("GreetMe from XmlRPC");
        template.sendBody("direct:response", "GreetMe from XmlRPC");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/xmlrpc/SpringXmlRpcDataFormatTest.xml");
    }

}
