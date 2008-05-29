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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spring.Main;



/**
 * The LoanBroker is a RouteBuilder which builds the whole loan message routing rules
 *
 * @version $
 */
public class LoanBroker extends RouteBuilder {

    /**
     * A main() so we can easily run these routing rules in our IDE
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        CamelContext context = new DefaultCamelContext();
        JmsBroker broker = new JmsBroker();
        broker.start();
        // Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        // Note we can explicity name the component
        context.addComponent("test-jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        context.addRoutes(new LoanBroker());
        context.start();

        // Start the loan broker
        Thread.sleep(5 * 60 * 1000);
        context.stop();
        Thread.sleep(1000);
        broker.stop();


    }

    /**
     * Lets configure the Camel routing rules using Java code...
     */
    public void configure() {

        from("test-jms:queue:loanRequestQueue").to("test-jms:queue:creditRequestQueue");
        from("test-jms:queue:creditRequestQueue").process(new CreditAgency()).to("test-jms:queue:creditResponseQueue");
        from("test-jms:queue:creditResponseQueue").multicast().to("test-jms:queue:bank1", "test-jms:queue:bank2", "test-jms:queue:bank3");
        from("test-jms:queue:bank1").process(new Bank("bank1")).to("test-jms:queue:bankReplyQueue");
        from("test-jms:queue:bank2").process(new Bank("bank2")).to("test-jms:queue:bankReplyQueue");
        from("test-jms:queue:bank3").process(new Bank("bank3")).to("test-jms:queue:bankReplyQueue");
        from("test-jms:queue:bankReplyQueue").aggregator(header(Constants.PROPERTY_CLIENT_ID), new BankResponseAggregationStrategy())
            .completedPredicate(header("aggregated").isEqualTo(3)).process(new Translator()).to("test-jms:queue:loanReply");


    }
}
