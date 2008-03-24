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
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;


public class CxfRouterTest extends ContextTestSupport {
    protected static final String ROUTER_ADDRESS = "http://localhost:9000/router";
    protected static final String SERVICE_ADDRESS = "http://localhost:9002/helloworld";
    protected static final String SERVICE_CLASS = "serviceClass=org.apache.camel.component.cxf.HelloService";

    private String routerEndpointURI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=POJO";
    private String serviceEndpointURI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=POJO";

    private ServerImpl server;
    private Bus bus;


    @Override
    protected void setUp() throws Exception {

        BusFactory.setDefaultBus(null);
        bus = BusFactory.getDefaultBus();
        super.setUp();
        startService();
    }

    protected void startService() {
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();

        svrBean.setAddress(SERVICE_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(bus);

        server = (ServerImpl)svrBean.create();
        server.start();
    }

    @Override
    protected void tearDown() throws Exception {
        //TODO need to shutdown the server
        super.tearDown();
        //server.stop();
        //bus.shutdown(false);
        BusFactory.setDefaultBus(null);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG").to(serviceEndpointURI);
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }


    public void testInvokingServiceFromCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(bus);

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", result, "echo hello world");

    }

    public void testOnwayInvocation() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(bus);
        HelloService client = (HelloService) proxyFactory.create();
        int count = client.getInvocationCount();
        client.ping();
        //oneway ping invoked, so invocationCount ++
        assertEquals("The ping should be invocated", client.getInvocationCount(), ++count);
    }
}
