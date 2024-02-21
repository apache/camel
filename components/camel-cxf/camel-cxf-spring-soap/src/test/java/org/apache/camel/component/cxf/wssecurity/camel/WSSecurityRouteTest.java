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

import jakarta.xml.ws.BindingProvider;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.wssecurity.server.CxfServer;
import org.apache.camel.hello_world_soap_http.Greeter;
import org.apache.camel.hello_world_soap_http.GreeterService;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WSSecurityRouteTest extends CamelSpringTestSupport {
    static final int PORT = CXFTestSupport.getPort1();
    static CxfServer cxfServer;

    @BeforeAll
    public static void setupContext() throws Exception {
        cxfServer = new CxfServer();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
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

        ((BindingProvider) greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2()
                                                           + "/WSSecurityRouteTest/GreeterSignaturePort");

        assertEquals("Hello Security", greeter.greetMe("Security"), "Get a wrong response");
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

        ((BindingProvider) greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2()
                                                           + "/WSSecurityRouteTest/GreeterUsernameTokenPort");

        assertEquals("Hello Security", greeter.greetMe("Security"), "Get a wrong response");
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

        ((BindingProvider) greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2()
                                                           + "/WSSecurityRouteTest/GreeterEncryptionPort");

        assertEquals("Hello Security", greeter.greetMe("Security"), "Get a wrong response");
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

        ((BindingProvider) greeter).getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:" + CXFTestSupport.getPort2()
                                                           + "/WSSecurityRouteTest/GreeterSecurityPolicyPort");

        assertEquals("Hello Security", greeter.greetMe("Security"), "Get a wrong response");
    }

}
