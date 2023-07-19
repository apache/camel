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
package org.apache.camel.component.web3j;

import io.reactivex.Flowable;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.protocol.Web3j;

public class Web3jMockTestSupport extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResult;

    @EndpointInject("mock:error")
    protected MockEndpoint mockError;

    @Mock
    @BindToRegistry("mockWeb3j")
    protected Web3j mockWeb3j;

    @Mock
    protected Flowable subscription;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    protected String getUrl() {
        return "web3j://http://127.0.0.1:8545?web3j=#mockWeb3j&";
    }

    protected Exchange createExchangeWithBodyAndHeader(Object body, String key, Object value) {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(key, value);
        return exchange;
    }

    @BeforeAll
    public static void startServer() {
    }

    @AfterAll
    public static void stopServer() {
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        try (AutoCloseable closeable = MockitoAnnotations.openMocks(this)) {
            super.setUp();
        }
    }
}
