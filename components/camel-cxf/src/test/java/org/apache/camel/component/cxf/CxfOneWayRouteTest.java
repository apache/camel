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

package org.apache.camel.component.cxf;

import java.io.ByteArrayOutputStream;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.Before;
import org.junit.Test;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


/**
 * Tests a cxf routing scenario from an oneway cxf EP to a file EP to not forward the old input
 * back to the oneway cxf EP.
 */
public class CxfOneWayRouteTest extends CamelSpringTestSupport {
    private static final QName SERVICE_NAME = new QName("http://apache.org/hello_world_soap_http", "SOAPService");
    private static final QName PORT_NAME = new QName("http://apache.org/hello_world_soap_http", "SoapPort");
    private static final String ROUTER_ADDRESS = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfOneWayRouteTest/router";

    private static Exception bindingException;
    private static boolean bindingDone;
    
    @Before
    public void setup() {
        bindingException = null;
        bindingDone = false;
    }
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // we can put the http conduit configuration here
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfOneWayRouteBeans.xml");
    }
    
    protected Greeter getCXFClient() throws Exception {
        Service service = Service.create(SERVICE_NAME);
        service.addPort(PORT_NAME, "http://schemas.xmlsoap.org/soap/", ROUTER_ADDRESS);
        Greeter greeter = service.getPort(PORT_NAME, Greeter.class);
        return greeter;
    }

    @Test
    public void testInvokingOneWayServiceFromCXFClient() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/camel-file/cxf-oneway-route");
        
        Greeter client = getCXFClient();
        client.greetMeOneWay("lemac");

        // may need to wait until the oneway call completes 
        long waitUntil = System.currentTimeMillis() + 10000;
        while (!bindingDone && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(1000);
        }

        assertMockEndpointsSatisfied();
        assertNull("exception occured: " + bindingException, bindingException);
    }
    
    public static class TestProcessor implements Processor {
        static final byte[] MAGIC = {(byte)0xca, 0x3e, 0x1e};

        public void process(Exchange exchange) throws Exception {
            // just check the MEP here
            assertEquals("Don't get the right MEP", ExchangePattern.InOnly, exchange.getPattern());
            // adding some binary segment
            String msg = exchange.getIn().getBody(String.class);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(MAGIC);
            bos.write(msg.getBytes());
            exchange.getIn().setBody(bos.toByteArray());
        }
    }
    
    public static class TestCxfBinding extends DefaultCxfBinding {
        @Override
        public void populateCxfResponseFromExchange(Exchange camelExchange, org.apache.cxf.message.Exchange cxfExchange) {
            try {
                super.populateCxfResponseFromExchange(camelExchange, cxfExchange);
            } catch (RuntimeException e) {
                bindingException = e;
                throw e;
            } finally {
                bindingDone = true;
            }
        }
        
    }
}
