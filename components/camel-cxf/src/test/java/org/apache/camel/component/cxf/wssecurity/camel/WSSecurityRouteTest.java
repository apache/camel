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
package org.apache.camel.component.cxf.wssecurity.camel;

import java.net.URL;

import javax.xml.ws.BindingProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.wssecurity.server.CxfServer;
import org.apache.camel.hello_world_soap_http.Greeter;
import org.apache.camel.hello_world_soap_http.GreeterService;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class WSSecurityRouteTest extends CamelTestSupport {
    static final int PORT = CXFTestSupport.getPort1();
    static CxfServer cxfServer;
    
    private static AbstractXmlApplicationContext applicationContext;

    @BeforeClass
    public static void setupContext() throws Exception {
        cxfServer = new CxfServer();
        applicationContext = createApplicationContext();
    }
    
    @AfterClass
    public static void shutdownService() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }

    private static ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/wssecurity/camel/camel-context.xml");
    }
    
    @Test
    public void testSignature() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = WSSecurityRouteTest.class.getResource("../client/wssec.xml");

        Bus bus = bf.createBus(busFile.toString());
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        
        GreeterService gs = new GreeterService();
        Greeter greeter = gs.getGreeterSignaturePort();
         
        ((BindingProvider)greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2() 
                + "/WSSecurityRouteTest/GreeterSignaturePort"
        );
        
        assertEquals("Get a wrong response", "Hello Security", greeter.greetMe("Security"));
    }
    
    @Test
    public void testUsernameToken() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = WSSecurityRouteTest.class.getResource("../client/wssec.xml");

        Bus bus = bf.createBus(busFile.toString());
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        
        GreeterService gs = new GreeterService();
        Greeter greeter = gs.getGreeterUsernameTokenPort();
         
        ((BindingProvider)greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2() 
                + "/WSSecurityRouteTest/GreeterUsernameTokenPort"
        );
        
        assertEquals("Get a wrong response", "Hello Security", greeter.greetMe("Security"));
    }
    
    @Test
    public void testEncryption() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = WSSecurityRouteTest.class.getResource("../client/wssec.xml");

        Bus bus = bf.createBus(busFile.toString());
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        
        GreeterService gs = new GreeterService();
        Greeter greeter = gs.getGreeterEncryptionPort();
         
        ((BindingProvider)greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2() 
                + "/WSSecurityRouteTest/GreeterEncryptionPort"
        );
        
        assertEquals("Get a wrong response", "Hello Security", greeter.greetMe("Security"));
    }
   
    @Test
    public void testSecurityPolicy() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = WSSecurityRouteTest.class.getResource("../client/wssec.xml");

        Bus bus = bf.createBus(busFile.toString());
        BusFactory.setDefaultBus(bus);
        BusFactory.setThreadDefaultBus(bus);
        
        GreeterService gs = new GreeterService();
        Greeter greeter = gs.getGreeterSecurityPolicyPort();
         
        ((BindingProvider)greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2() 
                + "/WSSecurityRouteTest/GreeterSecurityPolicyPort"
        );
        
        assertEquals("Get a wrong response", "Hello Security", greeter.greetMe("Security"));
    }
 
}
