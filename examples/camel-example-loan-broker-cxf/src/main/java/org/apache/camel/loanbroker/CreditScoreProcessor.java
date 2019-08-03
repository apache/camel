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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.loanbroker.credit.CreditAgencyWS;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

/**
 * Credit score processor.
 */
//START SNIPPET: credit
public class CreditScoreProcessor implements Processor {
    private String creditAgencyAddress;
    private CreditAgencyWS proxy;

    public CreditScoreProcessor(String address) {
        creditAgencyAddress = address;
        proxy = getProxy();
    }

    private CreditAgencyWS getProxy() {
        // Here we use JaxWs front end to create the proxy
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(creditAgencyAddress);
        clientBean.setServiceClass(CreditAgencyWS.class);
        clientBean.setBus(BusFactory.getDefaultBus());
        return (CreditAgencyWS) proxyFactory.create();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        List<Object> request = exchange.getIn().getBody(List.class);

        String ssn = (String) request.get(0);
        Double amount = (Double) request.get(1);
        Integer loanDuration = (Integer) request.get(2);
        int historyLength = proxy.getCreditHistoryLength(ssn);
        int score = proxy.getCreditScore(ssn);

        // create the invocation message for Bank client
        List<Object> bankRequest = new ArrayList<>();
        bankRequest.add(ssn);
        bankRequest.add(amount);
        bankRequest.add(loanDuration);
        bankRequest.add(historyLength);
        bankRequest.add(score);
        exchange.getOut().setBody(bankRequest);
        exchange.getOut().setHeader("operationName", "getQuote");
    }

}
