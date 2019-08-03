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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CxfSimpleRouterTest extends CamelTestSupport {    
    
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.camel.component.cxf.HelloService";
    
    protected Server server;
    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    private String serviceEndpointURI = "cxf://" + getServiceAddress() + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    
    protected String getRouterAddress() {
        return "http://localhost:" + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/router";
    }

    protected String getServiceAddress() {
        return "http://localhost:" + CXFTestSupport.getPort2() + "/" + getClass().getSimpleName() + "/helloworld";
    }
    
    protected void configureFactory(ServerFactoryBean svrBean) {
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Before
    public void startService() {       
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();
    
        svrBean.setAddress(getServiceAddress());
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        configureFactory(svrBean);
        server = svrBean.create();
        server.start();
    }
    
    @After
    public void shutdownService() {
        if (server != null) {
            server.stop();
        }
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                from(routerEndpointURI)
                    .to("log:org.apache.camel?level=DEBUG")
                    .to(serviceEndpointURI);
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }
    
    protected HelloService getCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(getRouterAddress());
        clientBean.setServiceClass(HelloService.class); 
        
        HelloService client = (HelloService) proxyFactory.create();
        return client;
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {        
        HelloService client = getCXFClient();
        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", result, "echo hello world");

    }

    @Test
    public void testOnwayInvocation() throws Exception {
        HelloService client = getCXFClient();
        int count = client.getInvocationCount();
        client.ping();
        //oneway ping invoked, so invocationCount ++
        assertEquals("The ping should be invocated", client.getInvocationCount(), ++count);
    }

}
