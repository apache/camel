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
package org.apache.camel.itest.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class DynamicRouteTest extends CamelTestSupport {
    
    @Test
    public void testDynamicRouteWithJms() throws Exception {
        String response = template.requestBody("jms:queue:request?replyTo=bar", "foo", String.class);
        assertEquals("response is foo", response);
        response = template.requestBody("jms:queue:request", "bar", String.class);
        assertEquals("response is bar", response);
        
      
    }
    
    @Test
    public void testDynamicRouteWithDirect() throws Exception {
        String response = template.requestBody("direct:start", "foo", String.class);
        assertEquals("response is foo", response);
        response = template.requestBody("direct:start", "bar", String.class);
        assertEquals("response is bar", response);
        
      
    }
    
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
       
        return new RouteBuilder() {
            public void configure() {
                from("jms:queue:request") 
                    .dynamicRouter().method(MyDynamicRouter.class, "route");
                from("direct:start")
                    .dynamicRouter(method(new MyDynamicRouter()));
            }

        };
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);
        registry.bind("jms", amq);
        registry.bind("myBean", new MyBean());
    }
    
    public static class MyBean {

        public String foo() {
            return "response is foo";
        }

        public String bar() {
            return "response is bar";
        }
    }
    
    public static class MyDynamicRouter {
        public String route(String methodName, @Header(Exchange.SLIP_ENDPOINT) String previous) {
            
            if (previous != null && previous.startsWith("bean://myBean?method")) {
                // we get the result here and stop routing
                return null;
            } else {
                return "bean:myBean?method=" + methodName;
            }
        }
    }

}

