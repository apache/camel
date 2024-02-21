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
package org.apache.camel.component.cxf.jms;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfEndpointJMSConsumerTest extends CamelSpringTestSupport {

    @RegisterExtension
    private static ArtemisService broker = ArtemisServiceFactory.createVMService();
    static {
        System.setProperty("CxfEndpointJMSConsumerTest.serviceAddress", broker.serviceAddress());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jms/camel-context.xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:jmsEndpoint").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // just set the response for greetme operation here
                        String me = exchange.getIn().getBody(String.class);
                        exchange.getMessage().setBody("Hello " + me);
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
                         + "=org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory"
                         + "&jndiConnectionFactoryName=ConnectionFactory&jndiURL="
                         + broker.serviceAddress();

        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(Greeter.class);
        factory.setAddress(address);
        Greeter greeter = factory.create(Greeter.class);
        String response = greeter.greetMe("Willem");
        assertEquals("Hello Willem", response, "Get a wrong response");
    }

}
