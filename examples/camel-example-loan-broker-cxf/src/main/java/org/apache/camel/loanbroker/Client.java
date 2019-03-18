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

import org.apache.camel.util.StopWatch;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;

/**
 * The client that will invoke the loan broker service
 */
//START SNIPPET: client
public final class Client {

    //Change the port to the one on which Loan broker is listening.

    private static String url = "http://localhost:9008/loanBroker";

    private Client() {
    }

    public static LoanBrokerWS getProxy(String address) {
        // Now we use the simple front API to create the client proxy
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(address);
        clientBean.setServiceClass(LoanBrokerWS.class);
        // just create a new bus for use
        clientBean.setBus(BusFactory.newInstance().createBus());
        return (LoanBrokerWS) proxyFactory.create();
    }

    public static void main(String[] args) {
        LoanBrokerWS loanBroker = getProxy(url);

        StopWatch watch = new StopWatch();
        String result = loanBroker.getLoanQuote("SSN", 5000.00, 24);

        System.out.println("Took " + watch.taken() + " milliseconds to call the loan broker service");
        System.out.println(result);
    }

}
//END SNIPPET: client

