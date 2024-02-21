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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfSpringRouterTest extends CamelSpringTestSupport {

    protected static final String SERVICE_CLASS = "serviceClass=org.apache.camel.component.cxf.HelloService";

    protected Server server;

    protected String getRouterAddress() {
        return "http://localhost:" + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/router";
    }

    protected String getServiceAddress() {
        return "http://localhost:" + CXFTestSupport.getPort2() + "/" + getClass().getSimpleName() + "/helloworld";
    }

    protected void configureFactory(ServerFactoryBean svrBean) {
    }

    @BeforeEach
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

    @AfterEach
    public void shutdownService() {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        CXFTestSupport.getPort1();
        super.setUp();

    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // Don't close the application context, as it will cause some trouble on the bus shutdown
        super.tearDown();

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:routerEndpoint").to("cxf:bean:serviceEndpoint");
            }
        };
    }

    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfSpringRouterBeans.xml");
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
        assertEquals("echo hello world", result, "we should get the right answer from router");

    }

    @Test
    public void testOnwayInvocation() throws Exception {
        HelloService client = getCXFClient();
        int count = client.getInvocationCount();
        client.ping();
        //oneway ping invoked, so invocationCount ++
        assertEquals(client.getInvocationCount(), ++count, "The ping should be invocated");
    }

}
