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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;



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
    // START SNIPPET: starting
    public static void main(String... args) throws Exception {

        CamelContext context = new DefaultCamelContext();
        JmsBroker broker = new JmsBroker();
        broker.start();
        // Set up the ActiveMQ JMS Components
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");

        // Note we can explicitly name the component
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        context.addRoutes(new LoanBroker());
        // Start the loan broker
        context.start();
        System.out.println("Server is ready");

        Thread.sleep(5 * 60 * 1000);
        context.stop();
        Thread.sleep(1000);
        broker.stop();

    }
    // END SNIPPET: starting

    /**
     * Lets configure the Camel routing rules using Java code...
     */
    public void configure() {
    // START SNIPPET: dsl
        // Put the message from loanRequestQueue to the creditRequestQueue
        from("jms:queue:loanRequestQueue").to("jms:queue:creditRequestQueue");

        // Now we can let the CreditAgency process the request, then the message will be put into creditResponseQueue
        from("jms:queue:creditRequestQueue").process(new CreditAgency()).to("jms:queue:creditResponseQueue");

        // Here we use the multicast pattern to send the message to three different bank queue
        from("jms:queue:creditResponseQueue").multicast().to("jms:queue:bank1", "jms:queue:bank2", "jms:queue:bank3");

        // Each bank processor will process the message and put the response message into the bankReplyQueue
        from("jms:queue:bank1").process(new Bank("bank1")).to("jms:queue:bankReplyQueue");
        from("jms:queue:bank2").process(new Bank("bank2")).to("jms:queue:bankReplyQueue");
        from("jms:queue:bank3").process(new Bank("bank3")).to("jms:queue:bankReplyQueue");

        // Now we aggregating the response message by using the Constants.PROPERTY_SSN header
        // The aggregation will completed when all the three bank responses are received
        from("jms:queue:bankReplyQueue")
            .aggregator(header(Constants.PROPERTY_SSN), new BankResponseAggregationStrategy())
            .completedPredicate(header(Exchange.AGGREGATED_COUNT).isEqualTo(3))

        // Here we do some translation and put the message back to loanReplyQueue
            .process(new Translator()).to("jms:queue:loanReplyQueue");

    // END SNIPPET: dsl
        
    // START SNIPPET: dsl-2
        // CreditAgency will get the request from parallelLoanRequestQueue
        from("jms:queue2:parallelLoanRequestQueue").process(new CreditAgency())
            // Set the aggregation strategy for aggregating the out message            
            .multicast(new BankResponseAggregationStrategy().setAggregatingOutMessage(true))
                // Send out the request the below three different banks parallelly
                .parallelProcessing(true).to("jms:queue2:bank1", "jms:queue2:bank2", "jms:queue2:bank3");
        
        // Each bank processor will process the message and put the response message back
        from("jms:queue2:bank1").process(new Bank("bank1"));
        from("jms:queue2:bank2").process(new Bank("bank2"));
        from("jms:queue2:bank3").process(new Bank("bank3"));
        

    // END SNIPPET: dsl-2
    }
}
