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
package org.apache.camel.example;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.example.server.Multiplier;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJmsClientServerTest extends Assert {
    protected static ConfigurableApplicationContext serverContext;
    
    @BeforeClass
    public static void setUpServer() {
        serverContext = new ClassPathXmlApplicationContext("/META-INF/spring/camel-server.xml");
    }
    
    @AfterClass
    public static void tearDownServer() {
        if (serverContext != null) {
            serverContext.stop();
        }
    }
    
    @Test
    public void testCamelClientInvocation() {
        ConfigurableApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");

        // get the camel template for Spring template style sending of messages (= producer)
        ProducerTemplate camelTemplate = (ProducerTemplate) context.getBean("camelTemplate");
        
        // as opposed to the CamelClientRemoting example we need to define the service URI in this java code
        int response = (Integer)camelTemplate.sendBody("jms:queue:numbers", ExchangePattern.InOut, 22);
        
        assertEquals("Get a wrong response", 66, response);
        
        context.stop();
    }
    
    @Test
    public void testCamelEndpointInvocation() throws Exception {
        ConfigurableApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");
        CamelContext camel = (CamelContext) context.getBean("camel-client");

        // get the endpoint from the camel context
        Endpoint endpoint = camel.getEndpoint("jms:queue:numbers");

        // create the exchange used for the communication
        // we use the in out pattern for a synchronized exchange where we expect a response
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        // set the input on the in body
        // must you correct type to match the expected type of an Integer object
        exchange.getIn().setBody(11);

        // to send the exchange we need an producer to do it for us
        Producer producer = endpoint.createProducer();
        // start the producer so it can operate
        producer.start();

        // let the producer process the exchange where it does all the work in this oneline of code
        
        producer.process(exchange);

        // get the response from the out body and cast it to an integer
        int response = exchange.getOut().getBody(Integer.class);
        
        assertEquals("Get a wrong response.", 33, response);

        // stop and exit the client
        producer.stop();
        context.stop();
    }
    
    @Test
    public void testCamelRemotingInvocation() {
        ConfigurableApplicationContext context = new ClassPathXmlApplicationContext("camel-client-remoting.xml");
        // just get the proxy to the service and we as the client can use the "proxy" as it was
        // a local object we are invoking. Camel will under the covers do the remote communication
        // to the remote ActiveMQ server and fetch the response.
        Multiplier multiplier = (Multiplier)context.getBean("multiplierProxy");
       
        int response = multiplier.multiply(33);
        
        assertEquals("Get a wrong response", 99, response);
        
        context.stop();
    }

}
