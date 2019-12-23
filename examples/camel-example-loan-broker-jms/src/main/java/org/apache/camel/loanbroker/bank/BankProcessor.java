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
package org.apache.camel.loanbroker.bank;

import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.loanbroker.Constants;

//START SNIPPET: bank
public class BankProcessor implements Processor {
    private final String bankName;
    private final double primeRate;

    public BankProcessor(String name) {
        bankName = name;
        primeRate = 3.5;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String ssn = exchange.getIn().getHeader(Constants.PROPERTY_SSN, String.class);
        Integer historyLength = exchange.getIn().getHeader(Constants.PROPERTY_HISTORYLENGTH, Integer.class);
        Random rand = new Random();
        double rate = primeRate + (double) (historyLength / 12) / 10 + (rand.nextDouble()  * 10) / 10;

        // set reply details as headers
        exchange.getOut().setHeader(Constants.PROPERTY_BANK, bankName);
        exchange.getOut().setHeader(Constants.PROPERTY_SSN, ssn);
        exchange.getOut().setHeader(Constants.PROPERTY_RATE, rate);
    }

}
//END SNIPPET: bank
