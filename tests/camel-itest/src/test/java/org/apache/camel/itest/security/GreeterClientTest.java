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
package org.apache.camel.itest.security;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.camel.CamelContext;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@ContextConfiguration(locations = {"camel-context.xml"})
public class GreeterClientTest extends AbstractJUnit4SpringContextTests {
    private static final java.net.URL WSDL_LOC;
    static {
        java.net.URL tmp = null;
        try {
            tmp = GreeterClientTest.class.getClassLoader().getResource("wsdl/hello_world.wsdl");
        } catch (final Exception e) {
            e.printStackTrace();
        }
        WSDL_LOC = tmp;
    }
    private static final QName SERVICE_QNAME =
        new QName("http://apache.org/hello_world_soap_http", "SOAPService");
    
    private static final QName PORT_QNAME =
        new QName("http://apache.org/hello_world_soap_http", "SoapOverHttp"
        );
    
    @Autowired
    protected CamelContext camelContext;
    
    protected String sendMessageWithUsernameToken(String username, String password, String message) throws Exception {
        final javax.xml.ws.Service svc = javax.xml.ws.Service.create(WSDL_LOC, SERVICE_QNAME);
        final Greeter greeter = svc.getPort(PORT_QNAME, Greeter.class);

        Client client = ClientProxy.getClient(greeter);        
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("action", "UsernameToken");
        props.put("user", username);
        // Set the the password type to be plain text, 
        // so we can keep using the password to authenticate with spring security
        props.put("passwordType", "PasswordText");       
        WSS4JOutInterceptor wss4jOut = new WSS4JOutInterceptor(props);

        client.getOutInterceptors().add(wss4jOut);
        ((BindingProvider)greeter).getRequestContext().put("password", password);
        return greeter.greetMe(message);
    }

    @Test
    public void testServiceWithValidateUser() throws Exception {
        
        String response = sendMessageWithUsernameToken("jim", "jimspassword", "CXF");
        
        assertEquals(" Hello CXF", response);

        try {
            sendMessageWithUsernameToken("jim", "foo", "CXF");
            fail("should fail");
        } catch (Exception ex) {
            String msg = ex.getMessage();
            assertTrue("Get a wrong type exception.", ex instanceof SOAPFaultException);
            assertTrue("Get a wrong exception message: " + msg,
                       msg.startsWith("The security token could not be authenticated or authorized")
                       || msg.startsWith("A security error was encountered when verifying the messag"));
        }

    }
    
    @Test
    public void testServiceWithNotAuthorizedUser() throws Exception {
        try {
            // this user doesn't have the right to access the processor
            sendMessageWithUsernameToken("bob", "bobspassword", "CXF");
            fail("should fail");
        } catch (Exception ex) {
            assertTrue("Get a wrong type exception.", ex instanceof SOAPFaultException);
            assertTrue("Get a wrong exception message", ex.getMessage().startsWith("Cannot access the processor which has been protected."));
            assertTrue("Get a wrong exception message", ex.getMessage().endsWith("Caused by: [org.springframework.security.access.AccessDeniedException - Access is denied]"));
        }        
    }

}
