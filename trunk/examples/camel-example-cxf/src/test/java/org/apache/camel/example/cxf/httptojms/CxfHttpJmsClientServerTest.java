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
package org.apache.camel.example.cxf.httptojms;

import java.net.MalformedURLException;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.PingMeFault;
import org.apache.hello_world_soap_http.types.FaultDetail;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfHttpJmsClientServerTest extends CamelSpringTestSupport {
    private static final String ROUTER_ADDRESS = "http://localhost:{{routerPort}}/SoapContext/SoapPort";

    @Test
    public void testClientInvocation() throws MalformedURLException {
        String address = ROUTER_ADDRESS.replace("{{routerPort}}", System.getProperty("routerPort"));
        
        Client client = new Client(address + "?wsdl");
        Greeter proxy = client.getProxy();

        String resp;
        resp = proxy.sayHi();
        assertEquals("Get a wrong response", "Bonjour", resp);

        resp = proxy.greetMe("Willem");
        assertEquals("Get a wrong response", "Hello Willem", resp);

        proxy.greetMeOneWay(System.getProperty("user.name"));

        try {
            proxy.pingMe("hello");
            fail("exception expected but none thrown");
        } catch (PingMeFault ex) {
            FaultDetail detail = ex.getFaultInfo();
            assertEquals("Wrong FaultDetail major:", 2, detail.getMajor());
            assertEquals("Wrong FaultDetail minor:", 1, detail.getMinor());
        }
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"/META-INF/spring/HttpToJmsCamelContext.xml"});
    }

}
