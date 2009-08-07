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
package org.apache.camel.example.cxf;

import java.net.MalformedURLException;

import javax.xml.ws.ProtocolException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.cxf.CamelCxfExample.MyRouteBuilder;
import org.apache.camel.example.jms.JmsBroker;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.PingMeFault;
import org.apache.hello_world_soap_http.types.FaultDetail;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CxfHttpJmsClientServerTest extends CamelTestSupport {
    static JmsBroker broker = new JmsBroker();
    static Server server = new Server();
    private static final String ROUTER_ADDRESS = "http://localhost:9001/SoapContext/SoapPort";
    
    @BeforeClass
    public static void startUpJmsBroker() throws Exception {
        broker.start();
        server.start();
    }
    
    @AfterClass
    public static void shutDownJmsBroker() throws Exception {
        server.stop();
        broker.stop();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new MyRouteBuilder();
    }
    
    @Test
    public void testClientInvocation() throws MalformedURLException {
        Client client = new Client(ROUTER_ADDRESS + "?wsdl");
        Greeter proxy = client.getProxy();
        
        String resp;
        resp = proxy.sayHi();
        assertEquals("Get a wrong response", "Bonjour", resp);
       
        resp = proxy.greetMe("Willem");
        assertEquals("Get a wrong response", "Hello Willem", resp);
        
        proxy.greetMeOneWay(System.getProperty("user.name"));

        try {
            proxy.pingMe("hello");
            fail("expect exception here");
        } catch (PingMeFault ex) {
            FaultDetail detail = ex.getFaultInfo();
            assertEquals("Wrong FaultDetail major:", 2, detail.getMajor());
            assertEquals("Wrong FaultDetail minor:", 1, detail.getMinor());
        }
    }
    
    

}
