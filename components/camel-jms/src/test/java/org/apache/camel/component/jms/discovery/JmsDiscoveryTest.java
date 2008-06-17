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
package org.apache.camel.component.jms.discovery;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * @version $Revision$
 */
public class JmsDiscoveryTest extends ContextTestSupport {
    protected MyRegistry registry = new MyRegistry();

    public void testDiscovery() throws Exception {
        // lets wait to see if we get 3 services
        for (int i = 0; i < 15; i++) {
            Thread.sleep(1000);
            if (registry.getServices().size() == 3) {
                break;
            }
        }

        Map<String, Map> map = new HashMap<String, Map>(registry.getServices());
        assertEquals("Size of map: " + map, 3, map.size());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("service1", new MyService("service1"));
        context.bind("service2", new MyService("service2"));
        context.bind("service3", new MyService("service3"));
        context.bind("registry", registry);
        return context;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets setup the heartbeats
                from("bean:service1?method=status").to("activemq:topic:registry.heartbeats");
                from("bean:service2?method=status").to("activemq:topic:registry.heartbeats");
                from("bean:service3?method=status").to("activemq:topic:registry.heartbeats");

                from("activemq:topic:registry.heartbeats?cacheLevelName=CACHE_CONSUMER").to("bean:registry?method=onEvent");
            }
        };
    }
}