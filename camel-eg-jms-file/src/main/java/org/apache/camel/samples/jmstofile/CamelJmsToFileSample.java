/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.samples.jmstofile;

import static
org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsExchange;
import org.apache.camel.impl.DefaultCamelContext;

public class CamelJmsToFileSample  {
    
    
    public static void main(String args[]) throws Exception {
        CamelContext context = new DefaultCamelContext();
        
        //Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        context.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));
        //Endpoint<FileExchange> endpoint = context.getEndpoint("file://test");
        
        
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("jms:queue:test.a").to("file://test");
                
            }
        });
        //Camel client - a handy class for kicking off exchanges
        
        CamelClient client = new CamelClient(context);
        context.start();
        client.send("jms:queue:test.a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("foo");
            }
        });
        Thread.sleep(1000);
        context.stop();
    }
}