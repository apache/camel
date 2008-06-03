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
package org.apache.camel.loanbroker.webservice.version.credit;

import org.apache.camel.loanbroker.webservice.version.Constants;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

public class CreditAgencyClient {

    public CreditAgencyWS getProxy() {
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(Constants.CREDITAGENCY_ADDRESS);
        clientBean.setServiceClass(CreditAgencyWS.class);
        clientBean.setBus(BusFactory.getDefaultBus());
        return (CreditAgencyWS)proxyFactory.create();
    }

    public static void main(String[] args) {
        CreditAgencyClient client = new CreditAgencyClient();
        CreditAgencyWS proxy = client.getProxy();
        System.out.println("get credit history length" + proxy.getCreditHistoryLength("ssn"));
        System.out.println("get credit score" + proxy.getCreditScore("ssn"));
    }

}
