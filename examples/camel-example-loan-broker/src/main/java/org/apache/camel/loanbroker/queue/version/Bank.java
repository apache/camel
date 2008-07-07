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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//START SNIPPET: bank
public class Bank implements Processor {
    private static final transient Log LOG = LogFactory.getLog(Bank.class);
    private String bankName;
    private double primeRate;

    public Bank(String name) {
        bankName = name;
        primeRate = 3.5;
    }

    public void process(Exchange exchange) throws Exception {
        String ssn = (String)exchange.getIn().getHeader(Constants.PROPERTY_SSN);
        Integer historyLength = (Integer) exchange.getIn().getHeader(Constants.PROPERTY_HISTORYLENGTH);
        double rate = primeRate + (double)(historyLength / 12) / 10 + (double)(Math.random() * 10) / 10;
        LOG.info("The bank: " + bankName + " for client: " + ssn + " 's rate " + rate);
        exchange.getOut().setHeader(Constants.PROPERTY_RATE, new Double(rate));
        exchange.getOut().setHeader(Constants.PROPERTY_BANK, bankName);
        exchange.getOut().setHeader(Constants.PROPERTY_SSN, ssn);
        // Sleep some time
        try {
            Thread.sleep((int) (Math.random() * 10) * 100);
        } catch (InterruptedException e) {
            // Discard
        }
    }

}
//END SNIPPET: bank