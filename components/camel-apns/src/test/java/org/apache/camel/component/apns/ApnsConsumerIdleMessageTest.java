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
package org.apache.camel.component.apns;

import com.notnoop.apns.ApnsService;
import com.notnoop.apns.utils.ApnsServerStub;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.apns.factory.ApnsServiceFactory;
import org.apache.camel.component.apns.util.ApnsUtils;
import org.apache.camel.component.apns.util.TestConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test to verify that the polling consumer delivers an empty Exchange when the
 * sendEmptyMessageWhenIdle property is set and a polling event yields no results.
 */
@Ignore // TODO Figure out why this test is failing and fix it.
public class ApnsConsumerIdleMessageTest extends CamelTestSupport {
    
    ApnsServerStub server;

    @Before
    public void startup() throws InterruptedException {
        server = ApnsUtils.prepareAndStartServer(TestConstants.TEST_GATEWAY_PORT, TestConstants.TEST_FEEDBACK_PORT);
    }

    @After
    public void stop() {
        server.stop();
    }
    
    @Test
    public void testConsumeIdleMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        
        Thread.sleep(1100);
        // cycling the server after first polling cycle because it can not handle reconnects for fast-cycle polling
        server.stop();
        server.start();
        Thread.sleep(1100);
        server.stop();
        assertMockEndpointsSatisfied();
        assertTrue(mock.getExchanges().get(0).getIn().getBody() == null);
        assertTrue(mock.getExchanges().get(1).getIn().getBody() == null);
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ApnsServiceFactory apnsServiceFactory = ApnsUtils.createDefaultTestConfiguration(camelContext);
        ApnsService apnsService = apnsServiceFactory.getApnsService();

        ApnsComponent apnsComponent = new ApnsComponent(apnsService);
        camelContext.addComponent("apns", apnsComponent);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("apns:consumer?initialDelay=1&delay=1&timeUnit=SECONDS&useFixedDelay=true"
                      + "&sendEmptyMessageWhenIdle=true")
                    .to("log:com.apache.camel.component.apns?showAll=true&multiline=true")
                    .to("mock:result");
            }
        };
    }
}
