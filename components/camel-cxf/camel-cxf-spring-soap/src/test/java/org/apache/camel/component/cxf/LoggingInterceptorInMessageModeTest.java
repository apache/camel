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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.spring.jaxws.CxfSpringEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Interceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class LoggingInterceptorInMessageModeTest {
    protected static AvailablePortFinder.Port port1;
    protected static AvailablePortFinder.Port port2;

    protected static String routerAddress;
    protected static String serviceAddress;

    static Server server;

    @Autowired
    protected CamelContext context;

    @BeforeAll
    public static void startService() {
        // allocated as late as possible, before the Spring context is created
        port1 = AvailablePortFinder.find();
        System.setProperty("LoggingInterceptorInMessageModeTest.port1", port1.toString());
        port2 = AvailablePortFinder.find();
        System.setProperty("LoggingInterceptorInMessageModeTest.port2", port2.toString());
        routerAddress = "http://localhost:" + port1.getPort() + "/LoggingInterceptorInMessageModeTest/router";
        serviceAddress = "http://localhost:" + port2.getPort() + "/LoggingInterceptorInMessageModeTest/helloworld";
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();

        svrBean.setAddress(serviceAddress);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());

        server = svrBean.create();
    }

    @AfterAll
    public static void stopService() {
        server.stop();
        server.destroy();
        System.clearProperty("LoggingInterceptorInMessageModeTest.port1");
        System.clearProperty("LoggingInterceptorInMessageModeTest.port2");
        port1.release();
        port2.release();
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {

        LoggingOutInterceptor logInterceptor = null;

        for (Interceptor<?> interceptor : context.getEndpoint("cxf:bean:serviceEndpoint", CxfSpringEndpoint.class)
                .getOutInterceptors()) {
            if (interceptor instanceof LoggingOutInterceptor) {
                logInterceptor = LoggingOutInterceptor.class.cast(interceptor);
                break;
            }
        }

        assertNotNull(logInterceptor);
        // StringPrintWriter writer = new StringPrintWriter();
        // Unfortunately, LoggingOutInterceptor does not have a setter for writer so
        // we can't capture the output to verify.
        // logInterceptor.setPrintWriter(writer);

        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(routerAddress);
        clientBean.setServiceClass(HelloService.class);

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo("hello world");
        assertEquals("echo hello world", result, "we should get the right answer from router");
        //assertTrue(writer.getString().indexOf("hello world") > 0);

    }

    @SuppressWarnings("unused")
    private static final class StringPrintWriter extends PrintWriter {
        private StringPrintWriter() {
            super(new StringWriter());
        }

        private StringPrintWriter(int initialSize) {
            super(new StringWriter(initialSize));
        }

        private String getString() {
            flush();
            return ((StringWriter) out).toString();
        }
    }

}
