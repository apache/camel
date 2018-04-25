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
package org.apache.camel.component.web3j;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.protocol.Web3j;
import rx.Subscription;

public class Web3jTestSupport extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResult;

    @EndpointInject(uri = "mock:error")
    protected MockEndpoint mockError;

    @Mock
    protected Web3j mockWeb3j;

    @Mock
    protected Subscription subscription;


    public Web3jTestSupport() {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "DEBUG");
    }


    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("mockWeb3j", mockWeb3j);
        return registry;
    }

    protected String getUrl() {
        return "web3j://http://127.0.0.1:8545?web3j=#mockWeb3j&";
    }

//    protected String getUrl() {
//        return "web3j://http://127.0.0.1:8545?";
//    }

    protected Exchange createExchangeWithBodyAndHeader(Object body, String key, Object value) {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(key, value);
        return exchange;
    }

    @BeforeClass
    public static void startServer() throws Exception {
    }

    @AfterClass
    public static void stopServer() throws Exception {
    }

    @Override
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
    }
}
