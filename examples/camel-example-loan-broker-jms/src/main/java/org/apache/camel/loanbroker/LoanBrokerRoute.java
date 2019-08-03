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
package org.apache.camel.loanbroker;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.loanbroker.bank.BankProcessor;

/**
 * The route for the loan broker example.
 */
public class LoanBrokerRoute extends RouteBuilder {

    /**
     * Let's configure the Camel routing rules using Java code...
     */
    @Override
    public void configure() {
        // START SNIPPET: dsl-2
        from("jms:queue:loan")
            // let the credit agency do the first work
            .process(new CreditAgencyProcessor())
            // send the request to the three banks
            .multicast(new BankResponseAggregationStrategy()).parallelProcessing()
            .to("jms:queue:bank1", "jms:queue:bank2", "jms:queue:bank3")
            .end()
            // and prepare the reply message
            .process(new ReplyProcessor());

        // Each bank processor will process the message and put the response message back
        from("jms:queue:bank1").process(new BankProcessor("bank1"));
        from("jms:queue:bank2").process(new BankProcessor("bank2"));
        from("jms:queue:bank3").process(new BankProcessor("bank3"));
        // END SNIPPET: dsl-2
    }

}
