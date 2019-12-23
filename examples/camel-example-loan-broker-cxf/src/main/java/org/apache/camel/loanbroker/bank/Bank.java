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

//START SNIPPET: bankImpl
public class Bank implements BankWS {
    private String bankName;
    private double primeRate;

    public Bank(String name) {
        bankName = name;
        primeRate = 3.5;
    }

    @Override
    public String getBankName() {
        return bankName;
    }

    @Override
    public BankQuote getQuote(String ssn, double loanAmount, int loanDuration, int creditHistory, int creditScore) {
        Double rate = primeRate + (double) (loanDuration / 12) / 10 + Math.random() * 10 / 10;
        // Wait for a while
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // do nothing here
        }
        BankQuote result = new BankQuote(bankName, ssn, rate);
        return result;
    }

}
//END SNIPPET: bankImpl
