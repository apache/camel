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
package org.apache.camel.component.cxf.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelJBIClientProxyTest {
    private HelloService proxy;
    private ClassPathXmlApplicationContext applicationContext;
    
    @Before
    public void setUp() {
        // setup service
        applicationContext = new ClassPathXmlApplicationContext(new String[]{"/org/apache/camel/component/cxf/transport/CamelJBIClientProxy.xml"});
        applicationContext.start();
        proxy = (HelloService) applicationContext.getBean("client");
        assertNotNull("The proxy should not be null.", proxy);
    }
    
    
    @Test
    public void testCallFromProxy() throws Exception {
        // The echo parameter will be ignore, since the service has the fix response
        String response = proxy.echo("Hello World!");
        assertEquals("Get a wrong response ", "echo Hello World!", response);
    }
    
    @Test
    public void testCallFromCamel() throws Exception {
        // get camel context
        CamelContext context = (CamelContext) applicationContext.getBean("conduit_context");
        ProducerTemplate producer = context.createProducerTemplate();
        // The echo parameter will be ignore, since the service has the fix response
        String response = producer.requestBody("direct://jbiStart", "Hello World!", String.class);
        assertEquals("Get a wrong response ", "echo Hello World!", response);
    }
    
    @After
    public void tearDown() {
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
    
    
    

}
