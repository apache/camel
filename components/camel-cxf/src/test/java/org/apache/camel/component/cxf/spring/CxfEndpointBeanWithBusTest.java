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
package org.apache.camel.component.cxf.spring;


import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test for testing CXF bus injection.
 *
 * @version $Revision$
 */
public class CxfEndpointBeanWithBusTest extends CxfEndpointBeanTest {
    
    @Before
    public void setUp() throws Exception {
        ctx =  new ClassPathXmlApplicationContext(
                new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeansRouterWithBus.xml"});
    }
    
    @Test
    public void testBusInjectedBySpring() throws Exception {
        CamelContext camelContext = (CamelContext) ctx.getBean("camel");
        CxfEndpoint endpoint = (CxfEndpoint)camelContext.getEndpoint("cxf:bean:routerEndpoint");
        
        // verify the interceptor that is added by the logging feature
        assertTrue(endpoint.getBus().getInInterceptors().size() == 1);
        assertEquals(LoggingInInterceptor.class, endpoint.getBus().getInInterceptors().get(0).getClass());
    }

}
