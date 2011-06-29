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

import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Test;

public class CxfEndpointBeanTest extends AbstractSpringBeanTestSupport {
    private static int port1 = AvailablePortFinder.getNextAvailable(); 
    private static int port2 = AvailablePortFinder.getNextAvailable(); 
    
    static {
        System.setProperty("CxfEndpointBeanTest.port1", Integer.toString(port1));
        System.setProperty("CxfEndpointBeanTest.port2", Integer.toString(port2));
    }

    
    protected String[] getApplicationContextFiles() {
        return new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeans.xml"};
    }

    @Test
    public void testCxfEndpointBeanDefinitionParser() {
        CxfEndpoint routerEndpoint = (CxfEndpoint)ctx.getBean("routerEndpoint");
        assertEquals("Got the wrong endpoint address", "http://localhost:" + port1 + "/router", routerEndpoint.getAddress());
        assertEquals("Got the wrong endpont service class", "org.apache.camel.component.cxf.HelloService",
                         routerEndpoint.getServiceClass().getName());
        assertEquals("Got the wrong handlers size", 1, routerEndpoint.getHandlers().size());
        assertEquals("Got the wrong schemalocations size", 1, routerEndpoint.getSchemaLocations().size());
        assertEquals("Got the wrong schemalocation", "classpath:wsdl/Message.xsd", routerEndpoint.getSchemaLocations().get(0));
    }
   
}
