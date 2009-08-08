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
package org.apache.camel.loanbroker.webservice.version;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.loanbroker.webservice.version.bank.BankServer;
import org.apache.camel.loanbroker.webservice.version.credit.CreditAgencyServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LoadBrokerTest extends Assert {
    CamelContext camelContext;
    CreditAgencyServer creditAgencyServer;
    BankServer bankServer;
    
    @Before
    public void startServices() throws Exception {
        camelContext = new DefaultCamelContext();
        creditAgencyServer = new CreditAgencyServer();
        // Start the credit server
        creditAgencyServer.start();

        // Start the bank server
        bankServer = new BankServer();
        bankServer.start();

        // Start the camel context
        camelContext.addRoutes(new LoanBroker());
        camelContext.start();
    }
    
    @After
    public void stopServices() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
        if (bankServer != null) {
            bankServer.stop();
        }
        if (creditAgencyServer != null) {
            creditAgencyServer.stop();
        }
    }
    
    @Test
    public void testInvocation() {
        Client client = new Client();
        String result = null;
        LoanBrokerWS loanBroker = client.getProxy(Constants.LOANBROKER_ADDRESS);
        long startTime = System.currentTimeMillis();
        result = loanBroker.getLoanQuote("Sequential SSN", 1000.54, 10);
        long endTime = System.currentTimeMillis();
        long delta1 = endTime - startTime;
        assertTrue(result.startsWith("The best rate is [ ssn:Sequential SSN bank:bank"));

        LoanBrokerWS paralleLoanBroker = client.getProxy(Constants.PARALLEL_LOANBROKER_ADDRESS);
        startTime = System.currentTimeMillis();
        result = paralleLoanBroker.getLoanQuote("Parallel SSN", 1000.54, 10);
        endTime = System.currentTimeMillis();
        long delta2 = endTime - startTime;
        assertTrue(result.startsWith("The best rate is [ ssn:Parallel SSN bank:bank"));
        
        assertTrue(delta2 < delta1);
    }
 
}
