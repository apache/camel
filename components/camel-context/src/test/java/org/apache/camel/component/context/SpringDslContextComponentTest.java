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
package org.apache.camel.component.context;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Test defining a context component using the Spring XML DSL
 */
@ContextConfiguration
public class SpringDslContextComponentTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject(uri = "mock:results", context = "tester")
    private MockEndpoint resultEndpoint;

    @Produce(uri = "tester:start", context = "tester")
    private ProducerTemplate template;

    @Test
    public void testUsingContextComponent() throws Exception {
        Object accounts = applicationContext.getBean("accounts");
        logger.info("Found accounts: " + accounts);

        resultEndpoint.expectedHeaderReceived("received", "true");
        resultEndpoint.expectedMessageCount(2);

        template.sendBody("<purchaseOrder>one</purchaseOrder>");
        template.sendBody("<purchaseOrder>two</purchaseOrder>");

        resultEndpoint.assertIsSatisfied();

        List<Exchange> receivedExchanges = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : receivedExchanges) {
            Message in = exchange.getIn();
            logger.info("Received from: " + exchange.getFromEndpoint() + " headers: " + in.getHeaders() + " body: " + in.getBody());
        }
    }
}
