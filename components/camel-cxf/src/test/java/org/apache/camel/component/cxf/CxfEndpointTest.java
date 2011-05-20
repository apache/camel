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

import org.apache.camel.component.cxf.CxfEndpoint.CamelCxfClientImpl;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.CXFBusImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * A unit test for spring configured cxf endpoint.
 * 
 * @version 
 */
public class CxfEndpointTest extends Assert {
    
    private String routerEndpointURI = "cxf://http://localhost:9000/router"
        + "?serviceClass=org.apache.camel.component.cxf.HelloService"
        + "&dataFormat=POJO";
    private String wsdlEndpointURI = "cxf://http://localhost:9002/helloworld"
        + "?wsdlURL=classpath:person.wsdl"
        + "&serviceName={http://camel.apache.org/wsdl-first}PersonService"
        + "&portName={http://camel.apache.org/wsdl-first}soap"
        + "&dataFormat=PAYLOAD";

    @Test
    public void testSpringCxfEndpoint() throws Exception {
        ClassPathXmlApplicationContext ctx =
            new ClassPathXmlApplicationContext(new String[]{"org/apache/camel/component/cxf/spring/CxfEndpointBeans.xml"});
        CxfComponent cxfComponent = new CxfComponent(new SpringCamelContext(ctx));
        CxfSpringEndpoint endpoint = (CxfSpringEndpoint)cxfComponent.createEndpoint("cxf://bean:serviceEndpoint");

        ServerFactoryBean svf = new ServerFactoryBean();
        endpoint.configure(svf);
        assertEquals("Got the wrong endpoint address", svf.getAddress(), "http://localhost:9002/helloworld");
        assertEquals("Got the wrong endpont service class",
            svf.getServiceClass().getCanonicalName(),
            "org.apache.camel.component.cxf.HelloService");
    }
    
    @Test
    public void testSettingClientBus() throws Exception {
        CXFBusImpl bus = (CXFBusImpl) BusFactory.newInstance().createBus();
        bus.setId("oldCXF");
        BusFactory.setThreadDefaultBus(bus);
        
        CXFBusImpl newBus = (CXFBusImpl) BusFactory.newInstance().createBus();
        newBus.setId("newCXF");
        CxfComponent cxfComponent = new CxfComponent(new DefaultCamelContext());
        CxfEndpoint endpoint = (CxfEndpoint)cxfComponent.createEndpoint(routerEndpointURI);
        endpoint.setBus(newBus);
        CamelCxfClientImpl client = (CamelCxfClientImpl)endpoint.createClient();
        assertEquals("CamelCxfClientImpl should has the same bus with CxfEndpoint", newBus, client.getBus());
        
        endpoint = (CxfEndpoint)cxfComponent.createEndpoint(wsdlEndpointURI);
        endpoint.setBus(newBus);
        client = (CamelCxfClientImpl)endpoint.createClient();
        assertEquals("CamelCxfClientImpl should has the same bus with CxfEndpoint", newBus, client.getBus());
    }

}
