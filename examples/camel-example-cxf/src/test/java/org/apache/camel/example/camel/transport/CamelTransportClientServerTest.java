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
package org.apache.camel.example.camel.transport;

import java.net.MalformedURLException;

import junit.framework.TestCase;

import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.PingMeFault;
import org.apache.hello_world_soap_http.types.FaultDetail;


public class CamelTransportClientServerTest extends TestCase {
    private Server server = new Server();
    
    
    public void startUpServer() throws Exception {
        server.prepare();
        server.start();
    }
    
    
    public void shutDownServer() {
        server.stop();
    }
    
    
    public void testClientInvocation() throws Exception {
        startUpServer();
        
        Client client = new Client("http://localhost:9091/GreeterContext/GreeterPort");
        Greeter port = client.getProxy();
        
        assertNotNull("The proxy should not be null", port);
        String resp = port.sayHi();
        assertEquals("Get a wrong response ", "Bonjour from EndpointA", resp);        

        resp = port.sayHi();
        assertEquals("Get a wrong response ", "Bonjour from EndpointB", resp);  

       
        resp = port.greetMe("Mike");
        assertEquals("Get a wrong response ", "Hello Mike from EndpointA", resp);
        
        resp = port.greetMe("James");
        assertEquals("Get a wrong response ", "Hello James from EndpointB", resp);  
       
        port.greetMeOneWay(System.getProperty("user.name"));

        try {
            System.out.println("Invoking pingMe, expecting exception...");
            port.pingMe("hello");
            fail("expects the exception here");
        } catch (PingMeFault ex) {
            assertEquals("Get a wrong exception message", "PingMeFault raised by server EndpointB", ex.getMessage());
            FaultDetail detail = ex.getFaultInfo();
            assertEquals("Wrong FaultDetail major:", 2, detail.getMajor());
            assertEquals("Wrong FaultDetail minor:", 1, detail.getMinor());
        }
        
        shutDownServer();

    }

    

}
