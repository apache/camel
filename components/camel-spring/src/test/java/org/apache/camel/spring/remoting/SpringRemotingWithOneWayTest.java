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
package org.apache.camel.spring.remoting;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * @version 
 */
@ContextConfiguration
public class SpringRemotingWithOneWayTest extends SpringRunWithTestSupport {

    @Autowired
    protected IAsyncService myService;

    @EndpointInject(uri = "mock:results")
    protected MockEndpoint endpoint;

    @Test
    public void testAsyncInvocation() throws Exception {
        endpoint.expectedMessageCount(1);

        // we should not block even though there is no consumer on the endpoint!
        myService.doSomethingAsync("Hello");

        endpoint.assertIsSatisfied();

        List<Exchange> list = endpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.info("Received: " + exchange.getIn().getBody());
            ExchangePattern pattern = exchange.getPattern();
            assertEquals("Expected pattern on exchange: " + exchange, ExchangePattern.InOnly, pattern);
        }

    }
}
