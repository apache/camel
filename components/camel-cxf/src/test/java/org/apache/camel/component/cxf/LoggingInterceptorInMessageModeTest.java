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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.camel.CamelContext;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;


/**
 *
 * @version 
 */
@ContextConfiguration
public class LoggingInterceptorInMessageModeTest extends AbstractJUnit4SpringContextTests {
    
    protected static final String ROUTER_ADDRESS = "http://localhost:9000/router";
    protected static final String SERVICE_ADDRESS = "http://localhost:9002/helloworld";

    @Autowired
    protected CamelContext context;
    
    @BeforeClass
    public static void startService() {
        //start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();
    
        svrBean.setAddress(SERVICE_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
    
        svrBean.create();
        
    }
    
    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        
        LoggingOutInterceptor logInterceptor = null;
                  
        for (Interceptor<?> interceptor 
            : context.getEndpoint("cxf:bean:serviceEndpoint", CxfSpringEndpoint.class).getBean()
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
        clientBean.setAddress(ROUTER_ADDRESS);
        clientBean.setServiceClass(HelloService.class);

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo("hello world");
        assertEquals("we should get the right answer from router", result, "echo hello world");
        //assertTrue(writer.getString().indexOf("hello world") > 0);

    }
    
    @SuppressWarnings("unused")
    private final class StringPrintWriter extends PrintWriter {
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
