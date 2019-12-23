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

public class BankQuote {
    private String bankName;
    private String ssn;
    private Double rate;

    public BankQuote() {
    }

    public BankQuote(String name, String s, Double r) {
        bankName = name;
        ssn = s;
        rate = r;
    }

    public void setBankName(String name) {
        bankName = name;
    }

    public void setSsn(String s) {
        ssn = s;
    }

    public void setRate(Double r) {
        rate = r;
    }

    public String getBankName() {
        return bankName;
    }

    public String getSsn() {
        return ssn;
    }

    public Double getRate() {
        return rate;
    }

    @Override
    public String toString() {
        return "[ssn:" + ssn + " bank:" + bankName + " rate:" + rate + "]";
    }

}
