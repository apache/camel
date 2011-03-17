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


import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;

/**
 * The client that will invoke the loan broker service
 */

//START SNIPPET: client
public class Client {

    public LoanBrokerWS getProxy(String address) {
        // Now we use the simple front API to create the client proxy
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(address);
        clientBean.setServiceClass(LoanBrokerWS.class);
        clientBean.setBus(BusFactory.getDefaultBus());
        return (LoanBrokerWS) proxyFactory.create();
    }

    public static void main(String[] args) {
        Client client = new Client();
        String result = null;
        LoanBrokerWS loanBroker = client.getProxy(Constants.LOANBROKER_ADDRESS);
        long startTime = System.currentTimeMillis();
        result = loanBroker.getLoanQuote("Sequential SSN", 1000.54, 10);
        long endTime = System.currentTimeMillis();
        System.out.println("It takes " + (endTime - startTime) + " milliseconds to call the sequential loan broker service");
        System.out.println(result);

        LoanBrokerWS paralleLoanBroker = client.getProxy(Constants.PARALLEL_LOANBROKER_ADDRESS);
        startTime = System.currentTimeMillis();
        result = paralleLoanBroker.getLoanQuote("Parallel SSN", 1000.54, 10);
        endTime = System.currentTimeMillis();
        System.out.println("It takes " + (endTime - startTime) + " milliseconds to call the parallel loan broker service");
        System.out.println(result);
    }

}
//END SNIPPET: client