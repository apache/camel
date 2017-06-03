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
package org.apache.camel.component.cxf.jms;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfEndpointJMSConsumerTest extends CamelTestSupport {
    protected AbstractXmlApplicationContext applicationContext;

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
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jms/camel-context.xml");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:jmsEndpoint").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // just set the response for greetme operation here
                        String me = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Hello " + me);
                    }
                });
            }
        };
    }
    
    @Test
    public void testInvocation() {
        // Here we just the address with JMS URI
        String address = "jms:jndi:dynamicQueues/test.cxf.jmstransport.queue"
            + "?jndiInitialContextFactory"
            + "=org.apache.activemq.jndi.ActiveMQInitialContextFactory"
            + "&jndiConnectionFactoryName=ConnectionFactory&jndiURL="
            + "vm://localhost";
   
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(Greeter.class);
        factory.setAddress(address);
        Greeter greeter = factory.create(Greeter.class);
        String response = greeter.greetMe("Willem");
        assertEquals("Get a wrong response", "Hello Willem", response);
    }
    
    
}
