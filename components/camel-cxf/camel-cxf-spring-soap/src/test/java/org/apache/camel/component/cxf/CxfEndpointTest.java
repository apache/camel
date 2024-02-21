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

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxws.CxfComponent;
import org.apache.camel.component.cxf.jaxws.CxfConfigurer;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint.CamelCxfClientImpl;
import org.apache.camel.component.cxf.spring.jaxws.CxfSpringEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * A unit test for spring configured cxf endpoint.
 */
public class CxfEndpointTest {
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
        context.start();

        CxfEndpoint endpoint = context.getEndpoint(routerEndpointURI + "&continuationTimeout=800000",
                CxfEndpoint.class);
        assertEquals(800000, endpoint.getContinuationTimeout(), "Get a wrong continucationTimeout value");
    }

    @Test
    public void testSpringCxfEndpoint() throws Exception {

        ClassPathXmlApplicationContext ctx
                = new ClassPathXmlApplicationContext(new String[] { "org/apache/camel/component/cxf/CxfEndpointBeans.xml" });

        SpringCamelContext context = new SpringCamelContext(ctx);
        context.start();

        CxfComponent cxfComponent = new CxfComponent(context);
        CxfSpringEndpoint endpoint = (CxfSpringEndpoint) cxfComponent.createEndpoint("cxf://bean:serviceEndpoint");

        assertEquals(endpoint.getAddress(),
                "http://localhost:" + port2 + "/CxfEndpointTest/helloworld", "Got the wrong endpoint address");
        assertEquals("org.apache.camel.component.cxf.HelloService", endpoint.getServiceClass().getCanonicalName(),
                "Got the wrong endpont service class");
    }

    @Test
    public void testSettingClientBus() throws Exception {
        ExtensionManagerBus bus = (ExtensionManagerBus) BusFactory.newInstance().createBus();
        bus.setId("oldCXF");
        BusFactory.setThreadDefaultBus(bus);

        ExtensionManagerBus newBus = (ExtensionManagerBus) BusFactory.newInstance().createBus();
        newBus.setId("newCXF");

        CamelContext context = new DefaultCamelContext();
        context.start();

        CxfComponent cxfComponent = new CxfComponent(context);
        CxfEndpoint endpoint = (CxfEndpoint) cxfComponent.createEndpoint(routerEndpointURI);
        endpoint.setBus(newBus);
        CamelCxfClientImpl client = (CamelCxfClientImpl) endpoint.createClient();
        assertEquals(newBus, client.getBus(), "CamelCxfClientImpl should has the same bus with CxfEndpoint");

        endpoint = (CxfEndpoint) cxfComponent.createEndpoint(wsdlEndpointURI);
        endpoint.setBus(newBus);
        client = (CamelCxfClientImpl) endpoint.createClient();
        assertEquals(newBus, client.getBus(), "CamelCxfClientImpl should has the same bus with CxfEndpoint");
    }

    @Test
    public void testCxfEndpointConfigurer() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        CxfConfigurer configurer = mock(CxfConfigurer.class);
        Processor processor = mock(Processor.class);
        registry.bind("myConfigurer", configurer);
        CamelContext camelContext = new DefaultCamelContext(registry);
        camelContext.start();

        CxfComponent cxfComponent = new CxfComponent(camelContext);
        CxfEndpoint endpoint = (CxfEndpoint) cxfComponent.createEndpoint(routerEndpointURI + "&cxfConfigurer=#myConfigurer");

        Consumer consumer = endpoint.createConsumer(processor);
        consumer.start();

        verify(configurer).configure(isA(AbstractWSDLBasedEndpointFactory.class));
        verify(configurer).configureServer(isA(Server.class));

        reset(configurer);
        Producer producer = endpoint.createProducer();
        producer.start();

        verify(configurer).configure(isA(AbstractWSDLBasedEndpointFactory.class));
        verify(configurer).configureClient(isA(Client.class));
    }

}
