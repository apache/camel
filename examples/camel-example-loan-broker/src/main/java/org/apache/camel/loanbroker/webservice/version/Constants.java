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

public interface Constants {

    String LOANBROKER_ADDRESS = "http://localhost:9008/loanBroker";
    String PARALLEL_LOANBROKER_ADDRESS = "http://localhost:9008/parallelLoanBroker";
    String CREDITAGENCY_ADDRESS = "http://localhost:9006/creditAgency";
    String BANK1_ADDRESS = "http://localhost:9001/bank1";
    String BANK2_ADDRESS = "http://localhost:9002/bank2";
    String BANK3_ADDRESS = "http://localhost:9003/bank3";

    String LOANBROKER_SERVICE_CLASS = "org.apache.camel.loanbroker.webservice.version.LoanBrokerWS";
    String CREDITAGENCY_SERVICE_CLASS = "org.apache.camel.loanbroker.webservice.version.credit.CreditAgencyWS";
    String BANK_SERVICE_CLASS = "org.apache.camel.loanbroker.webservice.version.bank.BankWS";

    String LOANBROKER_URI = "cxf://" + LOANBROKER_ADDRESS + "?serviceClass=" + LOANBROKER_SERVICE_CLASS;
    String PARALLEL_LOANBROKER_URI = "cxf://" + PARALLEL_LOANBROKER_ADDRESS + "?serviceClass=" + LOANBROKER_SERVICE_CLASS;
    String CREDITAGNCY_URI = "cxf://" + CREDITAGENCY_ADDRESS  + "?serviceClass=" + CREDITAGENCY_SERVICE_CLASS;
    String BANK1_URI = "cxf://" + BANK1_ADDRESS + "?serviceClass=" + BANK_SERVICE_CLASS;
    String BANK2_URI = "cxf://" + BANK2_ADDRESS + "?serviceClass=" + BANK_SERVICE_CLASS;
    String BANK3_URI = "cxf://" + BANK3_ADDRESS + "?serviceClass=" + BANK_SERVICE_CLASS;



}
