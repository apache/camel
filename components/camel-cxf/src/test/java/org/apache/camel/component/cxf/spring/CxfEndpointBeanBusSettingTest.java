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
import org.apache.cxf.Bus;
import org.junit.Test;

public class CxfEndpointBeanBusSettingTest extends AbstractSpringBeanTestSupport {

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeansBusSetting.xml"};    
    }
    
    @Test
    public void testBusInjectedBySpring() throws Exception {
        CamelContext camelContext = (CamelContext) ctx.getBean("camel");
        
        CxfEndpoint endpoint = (CxfEndpoint)camelContext.getEndpoint("cxf:bean:routerEndpoint");
        assertEquals("Get a wrong endpoint uri", "cxf://bean:routerEndpoint", endpoint.getEndpointUri());       
        Bus cxf1 = endpoint.getBus();
        
        assertTrue(cxf1.getOutInterceptors().size() >= 1);
        assertTrue(cxf1.getInInterceptors().size() == 0);
        
        endpoint = (CxfEndpoint)camelContext.getEndpoint("cxf:bean:serviceEndpoint");
        assertEquals("Get a wrong endpoint uri", "cxf://bean:serviceEndpoint", endpoint.getEndpointUri());
        Bus cxf2 = endpoint.getBus();
        assertTrue(cxf2.getInInterceptors().size() >= 1);
        assertTrue(cxf2.getOutInterceptors().size() == 0);
        
    }

}
