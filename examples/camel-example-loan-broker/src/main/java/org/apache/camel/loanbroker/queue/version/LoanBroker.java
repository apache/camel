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
package org.apache.camel.loanbroker.queue.version;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Main class to start the loan broker server
 */
public final class LoanBroker {

    private LoanBroker() {
    }

    // START SNIPPET: starting
    public static void main(String... args) throws Exception {
        // setup an embedded JMS broker
        JmsBroker broker = new JmsBroker();
        broker.start();

        // create a camel context
        CamelContext context = new DefaultCamelContext();

        // Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:51616");
        // Note we can explicitly name the component
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        // add the route
        context.addRoutes(new LoanBrokerRoute());

        // start Camel
        context.start();
        System.out.println("Server is ready");

        // let it run for 5 minutes before shutting down
        Thread.sleep(5 * 60 * 1000);
        context.stop();
        Thread.sleep(1000);
        broker.stop();
    }
    // END SNIPPET: starting

}
