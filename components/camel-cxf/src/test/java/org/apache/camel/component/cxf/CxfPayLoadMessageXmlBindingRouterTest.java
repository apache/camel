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

import java.util.List;

import javax.xml.transform.Source;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class CxfPayLoadMessageXmlBindingRouterTest extends CamelTestSupport {
    
    protected static final String ROUTER_ADDRESS = "http://localhost:" 
        + CXFTestSupport.getPort1() + "/CxfPayLoadMessageXmlBindingRouterTest/router";
    protected static final String SERVICE_ADDRESS = "http://localhost:" 
        + CXFTestSupport.getPort2() + "/CxfPayLoadMessageXmlBindingRouterTest/helloworld";
       
    protected AbstractXmlApplicationContext applicationContext;
        
    protected static String getBindingId() {
        return "http://cxf.apache.org/bindings/xformat";
    }
    
    @BeforeClass
    public static void startService() {       
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();
    
        svrBean.setAddress(SERVICE_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBindingId(getBindingId());
    
        Server server = svrBean.create();
        server.start();
    }
    
    @Before
    public void setUp() throws Exception {       
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @After
    public void tearDown() throws Exception {
        
        IOHelper.close(applicationContext);
        super.tearDown();
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/XmlBindingRouterContext.xml");
    }
    
    protected HelloService getCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBindingId(getBindingId());
        
        HelloService client = (HelloService) proxyFactory.create();
        return client;
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {                
                from("cxf:bean:routerEndpoint?dataFormat=PAYLOAD").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        CxfPayload<?> payload = exchange.getIn().getBody(CxfPayload.class);
                        List<Source> elements = payload.getBodySources();
                        assertNotNull("We should get the elements here", elements);
                        assertEquals("Get the wrong elements size", elements.size(), 1);
                        
                        Element el = new XmlConverter().toDOMElement(elements.get(0));
                        assertEquals("Get the wrong namespace URI", el.getNamespaceURI(), "http://cxf.component.camel.apache.org/");
                    }
                    
                })
                .to("cxf:bean:serviceEndpoint?dataFormat=PAYLOAD");
                
            }
        };
    }
    
    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {        
        HelloService client = getCXFClient();
        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", result, "echo hello world");

        int count = client.getInvocationCount();
        client.ping();
        //oneway ping invoked, so invocationCount ++
        assertEquals("The ping should be invocated", client.getInvocationCount(), ++count);
    }

}
