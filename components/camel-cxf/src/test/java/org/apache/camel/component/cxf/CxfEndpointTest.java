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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.CxfEndpoint.CamelCxfClientImpl;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * A unit test for spring configured cxf endpoint.
 *
 * @version
 */
public class CxfEndpointTest extends Assert {
    private int port1 = CXFTestSupport.getPort1();
    private int port2 = CXFTestSupport.getPort2();

    private String routerEndpointURI = "cxf://http://localhost:" + port1 + "/CxfEndpointTest/router"
            + "?serviceClass=org.apache.camel.component.cxf.HelloService"
            + "&dataFormat=POJO";
    private String wsdlEndpointURI = "cxf://http://localhost:" + port2 + "/CxfEndpointTest/helloworld"
            + "?wsdlURL=classpath:person.wsdl"
            + "&serviceName={http://camel.apache.org/wsdl-first}PersonService"
            + "&portName={http://camel.apache.org/wsdl-first}soap"
            + "&dataFormat=PAYLOAD";

    @Test
    public void testSettingContinucationTimout() throws Exception {
        CamelContext context = new DefaultCamelContext();
        CxfEndpoint endpoint = context.getEndpoint(routerEndpointURI + "&continuationTimeout=800000",
                CxfEndpoint.class);
        assertEquals("Get a wrong continucationTimeout value", 800000, endpoint.getContinuationTimeout());
    }

    @Test
    public void testSpringCxfEndpoint() throws Exception {

        ClassPathXmlApplicationContext ctx =
                new ClassPathXmlApplicationContext(new String[]{"org/apache/camel/component/cxf/CxfEndpointBeans.xml"});
        CxfComponent cxfComponent = new CxfComponent(new SpringCamelContext(ctx));
        CxfSpringEndpoint endpoint = (CxfSpringEndpoint)cxfComponent.createEndpoint("cxf://bean:serviceEndpoint");

        assertEquals("Got the wrong endpoint address", endpoint.getAddress(),
                "http://localhost:" + port2 + "/CxfEndpointTest/helloworld");
        assertEquals("Got the wrong endpont service class",
                endpoint.getServiceClass().getCanonicalName(),
                "org.apache.camel.component.cxf.HelloService");
    }

    @Test
    public void testSettingClientBus() throws Exception {
        ExtensionManagerBus bus = (ExtensionManagerBus) BusFactory.newInstance().createBus();
        bus.setId("oldCXF");
        BusFactory.setThreadDefaultBus(bus);

        ExtensionManagerBus newBus = (ExtensionManagerBus) BusFactory.newInstance().createBus();
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

    @Test
    public void testCxfEndpointConfigurer() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        CxfEndpointConfigurer configurer = mock(CxfEndpointConfigurer.class);
        Processor processor = mock(Processor.class);
        registry.put("myConfigurer", configurer);
        CamelContext camelContext = new DefaultCamelContext(registry);
        CxfComponent cxfComponent = new CxfComponent(camelContext);
        CxfEndpoint endpoint = (CxfEndpoint)cxfComponent.createEndpoint(routerEndpointURI + "&cxfEndpointConfigurer=#myConfigurer");

        endpoint.createConsumer(processor);
        verify(configurer).configure(isA(AbstractWSDLBasedEndpointFactory.class));
        verify(configurer).configureServer(isA(Server.class));
        
        reset(configurer);
        Producer producer = endpoint.createProducer();
        producer.start();
        
        verify(configurer).configure(isA(AbstractWSDLBasedEndpointFactory.class));
        verify(configurer).configureClient(isA(Client.class));
    }

}
