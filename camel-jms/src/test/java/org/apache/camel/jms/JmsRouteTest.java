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
package org.apache.camel.jms;

import junit.framework.TestCase;
import org.apache.camel.CamelContainer;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.jms.JmsComponent.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.ConnectionFactory;

/**
 * @version $Revision$
 */
public class JmsRouteTest extends TestCase {
    public void testJmsRoute() throws Exception {
        CamelContainer container = new CamelContainer();

        System.out.println("Created container: " + container);
        
        // lets configure some componnets
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponent(connectionFactory));

        // lets add some routes
        container.routes(new RouteBuilder() {
            public void configure() {
                from("jms:activemq:FOO.BAR").to("jms:activemq:FOO.BAR");
            }
        });
    }
}
