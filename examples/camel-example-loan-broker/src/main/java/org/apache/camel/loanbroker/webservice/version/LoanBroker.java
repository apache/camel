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


import java.util.ArrayList;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.jms.JmsComponent;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.loanbroker.webservice.version.bank.BankServer;
import org.apache.camel.loanbroker.webservice.version.credit.CreditAgencyServer;
import org.apache.camel.loanbroker.webservice.version.credit.CreditAgencyWS;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spring.Main;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;



/**
 * The LoanBroker is a RouteBuilder which builds the whole loan message routing rules
 *
 *
 */
public class LoanBroker extends RouteBuilder {

    /**
     * A main() so we can easily run these routing rules in our IDE
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        CamelContext context = new DefaultCamelContext();
        CreditAgencyServer creditAgencyServer = new CreditAgencyServer();
        creditAgencyServer.start();
        BankServer bankServer = new BankServer();
        bankServer.start();

        context.addRoutes(new LoanBroker());
        context.start();

        // Start the loan broker
        Thread.sleep(5 * 60 * 1000);
        context.stop();
        Thread.sleep(1000);
        bankServer.stop();
        creditAgencyServer.stop();
    }

    /**
     * Lets configure the Camel routing rules using Java code...
     */
    public void configure() {
        // Option 1 to call the bank endpoints sequentially
        from(Constants.LOANBROKER_URI).process(new CreditScoreProcessor(Constants.CREDITAGENCY_ADDRESS))
            .multicast(new BankResponseAggregationStrategy()).to(Constants.BANK1_URI, Constants.BANK2_URI, Constants.BANK3_URI);

        // Option 2 to call the bank endpoints parallelly
        from(Constants.PARALLEL_LOANBROKER_URI).process(new CreditScoreProcessor(Constants.CREDITAGENCY_ADDRESS))
            .multicast(new BankResponseAggregationStrategy(), true).to(Constants.BANK1_URI, Constants.BANK2_URI, Constants.BANK3_URI);

    }

    class CreditScoreProcessor implements Processor {
        private String creditAgencyAddress;
        private CreditAgencyWS proxy;

        public CreditScoreProcessor(String address) {
            creditAgencyAddress = address;
            proxy = getProxy();
        }

        private CreditAgencyWS getProxy() {
            JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
            ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
            clientBean.setAddress(Constants.CREDITAGENCY_ADDRESS);
            clientBean.setServiceClass(CreditAgencyWS.class);
            clientBean.setBus(BusFactory.getDefaultBus());
            return (CreditAgencyWS)proxyFactory.create();
        }

        @SuppressWarnings("unchecked")
        public void process(Exchange exchange) throws Exception {
            Message requestMessage = exchange.getIn();
            List<Object> request = (List<Object>) requestMessage.getBody();

            String ssn = (String)request.get(0);
            Double amount = (Double) request.get(1);
            Integer loanDuriation = (Integer)request.get(2);
            int historyLength = proxy.getCreditHistoryLength(ssn);
            int score = proxy.getCreditScore(ssn);
            //exchange.getOut().setBody("The ssn's historyLength is " + historyLength + " score is " + score);

            // create the invocation message for Bank client
            List<Object> bankRequest = new ArrayList<Object>();
            bankRequest.add(ssn);
            bankRequest.add(amount);
            bankRequest.add(loanDuriation);
            bankRequest.add(historyLength);
            bankRequest.add(score);
            exchange.getOut().setBody(bankRequest);
            exchange.getOut().setHeader(CxfConstants.OPERATION_NAME, "getQuote");
        }

    }


}
