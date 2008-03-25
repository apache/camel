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
package org.apache.camel.component.jms.issues;


import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.issues.Issue170Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;


/**
 * @version $Revision$
 */
public class JmsIssue170Test extends Issue170Test {
    @Override
    protected void setUp() throws Exception {
        qOne = "activemq:Test.Q1";
        super.setUp();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(qOne);

                // write to Q3 but not to Q2
                from(qOne).to("activemq:Test.Q2", "activemq:Test.Q3");

                // subscribe from the JMS queues to send to the mocks for testing
                from("activemq:Test.Q2").to(qTwo);
                from("activemq:Test.Q3").to(qThree);
            }
        };
    }
}
