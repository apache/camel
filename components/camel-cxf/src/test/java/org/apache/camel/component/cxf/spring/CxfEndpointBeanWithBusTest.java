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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.junit.Test;

/**
 * Unit test for testing CXF bus injection.
 */
public class CxfEndpointBeanWithBusTest extends AbstractSpringBeanTestSupport {
    static int port1 = CXFTestSupport.getPort1();
    static int port2 = CXFTestSupport.getPort2();

        
    @Override
    protected String[] getApplicationContextFiles() {
        return new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeansRouterWithBus.xml"};
    }
    
    @Test
    public void testBusInjectedBySpring() throws Exception {
        CamelContext camelContext = ctx.getBean("camel", CamelContext.class);
        CxfEndpoint endpoint = camelContext.getEndpoint("cxf:bean:routerEndpoint", CxfEndpoint.class);

        // verify the interceptor that is added by the logging feature
        // Spring 3.0.0 has an issue of SPR-6589 which will call the BusApplicationListener twice for the same event,
        // so we will get more one InInterceptors here
        assertTrue(endpoint.getBus().getInInterceptors().size() >= 1);
        for (Interceptor<?> i : endpoint.getBus().getInInterceptors()) {
            if (i instanceof LoggingInInterceptor) {
                return;
            }
        }
        fail("Could not find the LoggingInInterceptor on the bus. " + endpoint.getBus().getInInterceptors());
    }
    
    @Test
    public void testCxfEndpointBeanDefinitionParser() {
        CxfEndpoint routerEndpoint = ctx.getBean("routerEndpoint", CxfEndpoint.class);
        assertEquals("Got the wrong endpoint address", "http://localhost:" + port1 
                     + "/CxfEndpointBeanWithBusTest/router/", routerEndpoint.getAddress());
        assertEquals("Got the wrong endpont service class", 
                     "org.apache.camel.component.cxf.HelloService",
                     routerEndpoint.getServiceClass().getName());
        
    }

}
