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
package org.apache.camel.component.cxf;

import java.util.concurrent.atomic.AtomicInteger;

import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.ws.Endpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.BeforeClass;
import org.junit.Test;

public class CxfPayLoadBareSoapTest extends CamelTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final String ORIGINAL_URL =
            String.format("http://localhost:%s/original/Service", PORT);
    private static final String PROXY_URL =
            String.format("http://localhost:%s/proxy/Service", PORT);
    private static final BareSoapServiceImpl IMPLEMENTATION = new BareSoapServiceImpl();

    @BeforeClass
    public static void startService() {
        Endpoint.publish(ORIGINAL_URL, IMPLEMENTATION);
    }

    protected String getRouterEndpointURI() {
        return String.format("cxf:%s?dataFormat=PAYLOAD&wsdlURL=classpath:bare.wsdl", PROXY_URL);
    }

    protected String getServiceEndpointURI() {
        return String.format("cxf:%s?dataFormat=PAYLOAD&wsdlURL=classpath:bare.wsdl", ORIGINAL_URL);
    }     
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getRouterEndpointURI()).to(getServiceEndpointURI());
            }
        };
    }
    
    @Test
    public void testInvokeProxyService() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(BareSoapService.class);
        factory.setAddress(PROXY_URL);
        factory.setBus(BusFactory.newInstance().createBus());
        BareSoapService client = (BareSoapService) factory.create();

        client.doSomething();

        assertEquals("Proxied service should have been invoked once", 1, IMPLEMENTATION.invocations.get());
    }

    @WebService
    @SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.BARE)
    public interface BareSoapService {
        void doSomething();
    }

    public static class BareSoapServiceImpl implements BareSoapService {
        private AtomicInteger invocations = new AtomicInteger(0);

        @Override
        public void doSomething() {
            invocations.incrementAndGet();
        }
    }
}
