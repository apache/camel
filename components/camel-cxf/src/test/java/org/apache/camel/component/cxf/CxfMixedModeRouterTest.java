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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class CxfMixedModeRouterTest extends CamelTestSupport {
    protected static int port1 = CXFTestSupport.getPort1(); 
    protected static int port2 = CXFTestSupport.getPort2(); 

    protected static Server server;
    protected static final String ROUTER_ADDRESS = "http://localhost:" + port1 + "/CxfMixedModeRouterTest/router";
    protected static final String SERVICE_ADDRESS = "http://localhost:" + port2 + "/CxfMixedModeRouterTest/helloworld";
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.camel.component.cxf.HelloService";

    private String routerEndpointURI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD&allowStreaming=false";
    private String serviceEndpointURI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @BeforeClass
    public static void startService() {       
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();
    
        svrBean.setAddress(SERVICE_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
    
        server = svrBean.create();
        server.start();
    }
    
    @AfterClass
    public static void shutdownService() {
        if (server != null) {
            server.stop();
        }
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                from(routerEndpointURI).process(new Processor() {

                    // convert request message
                    public void process(Exchange exchange) throws Exception {
                        CxfPayload<?> message =  exchange.getIn().getBody(CxfPayload.class);

                        List<String> params = new ArrayList<String>();

                        if (message != null) {
                            // convert CxfPayload to list of objects any way you like
                            Element element = new XmlConverter().toDOMElement(message.getBody().get(0));
                            params.add(element.getFirstChild().getTextContent());
                        }
                            
                        // replace the body
                        exchange.getIn().setBody(params);
                        
                        // if you need to change the operation name
                        //exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);      
                        
                    }
                     
                }).to(serviceEndpointURI).process(new Processor() {

                    // convert response to CxfPayload
                    public void process(Exchange exchange) throws Exception {

                        List<?>list = exchange.getIn().getBody(List.class);
                        CxfPayload<SoapHeader> message = null;
                        if (list != null) {
                            // convert the list of objects to CxfPayload any way you like
                            
                            String s = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" 
                                + "<ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">" 
                                + "<return xmlns=\"http://cxf.component.camel.apache.org/\">" 
                                + list.get(0) 
                                + "</return></ns1:echoResponse>";
                            List<Element> body = new ArrayList<Element>();
                            body.add(StaxUtils.read(new StringReader(s)).getDocumentElement());

                            message = new CxfPayload<SoapHeader>(new ArrayList<SoapHeader>(), body);
                        }
                        
                        exchange.getIn().setBody(message);
                        
                        // we probably should be smarter in detecting data format based on message body
                        // but for now we need to explicitly reset the mode (see CAMEL-3420)
                        exchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.PAYLOAD);   

                    }
                });
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }
    
    protected HelloService getCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.newInstance().createBus());
        
        HelloService client = (HelloService) proxyFactory.create();
        return client;
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {        
        HelloService client = getCXFClient();
        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", result, "echo hello world");

    }

}
