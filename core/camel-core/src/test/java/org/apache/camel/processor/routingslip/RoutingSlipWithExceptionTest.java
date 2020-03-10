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
package org.apache.camel.processor.routingslip;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

public class RoutingSlipWithExceptionTest extends ContextTestSupport {

    protected static final String ANSWER = "answer";
    protected static final String ROUTING_SLIP_HEADER = "destinations";
    protected MyBean myBean = new MyBean();
    private MockEndpoint endEndpoint;
    private MockEndpoint exceptionEndpoint;
    private MockEndpoint exceptionSettingEndpoint;
    private MockEndpoint aEndpoint;

    @Test
    public void testNoException() throws Exception {
        endEndpoint.expectedMessageCount(1);
        exceptionEndpoint.expectedMessageCount(0);
        aEndpoint.expectedMessageCount(1);

        sendRoutingSlipWithNoExceptionThrowingComponent();

        assertEndpointsSatisfied();
    }

    @Test
    public void testWithExceptionThrowingComponentFirstInList() throws Exception {
        endEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);
        aEndpoint.expectedMessageCount(0);

        sendRoutingSlipWithExceptionThrowingComponentFirstInList();

        assertEndpointsSatisfied();
    }

    @Test
    public void testWithExceptionThrowingComponentSecondInList() throws Exception {
        endEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);
        aEndpoint.expectedMessageCount(1);

        sendRoutingSlipWithExceptionThrowingComponentSecondInList();

        assertEndpointsSatisfied();
    }

    @Test
    public void testWithExceptionSettingComponentFirstInList() throws Exception {
        endEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);
        aEndpoint.expectedMessageCount(0);

        sendRoutingSlipWithExceptionSettingComponentFirstInList();

        assertEndpointsSatisfied();
    }

    @Test
    public void testWithExceptionSettingComponentSecondInList() throws Exception {
        endEndpoint.expectedMessageCount(0);
        exceptionEndpoint.expectedMessageCount(1);
        aEndpoint.expectedMessageCount(1);

        sendRoutingSlipWithExceptionSettingComponentSecondInList();

        assertEndpointsSatisfied();
    }

    private void assertEndpointsSatisfied() throws InterruptedException {
        MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, endEndpoint, exceptionEndpoint, aEndpoint);
    }

    protected void sendRoutingSlipWithExceptionThrowingComponentFirstInList() {
        template.sendBodyAndHeader("direct:start", ANSWER, ROUTING_SLIP_HEADER, "bean:myBean?method=throwException,mock:a");
    }

    protected void sendRoutingSlipWithExceptionThrowingComponentSecondInList() {
        template.sendBodyAndHeader("direct:start", ANSWER, ROUTING_SLIP_HEADER, "mock:a,bean:myBean?method=throwException");
    }

    protected void sendRoutingSlipWithNoExceptionThrowingComponent() {
        template.sendBodyAndHeader("direct:start", ANSWER, ROUTING_SLIP_HEADER, "mock:a");
    }

    protected void sendRoutingSlipWithExceptionSettingComponentFirstInList() {
        template.sendBodyAndHeader("direct:start", ANSWER, ROUTING_SLIP_HEADER, "mock:exceptionSetting,mock:a");
    }

    protected void sendRoutingSlipWithExceptionSettingComponentSecondInList() {
        template.sendBodyAndHeader("direct:start", ANSWER, ROUTING_SLIP_HEADER, "mock:a,mock:exceptionSetting");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        endEndpoint = resolveMandatoryEndpoint("mock:noexception", MockEndpoint.class);
        exceptionEndpoint = resolveMandatoryEndpoint("mock:exception", MockEndpoint.class);
        exceptionSettingEndpoint = resolveMandatoryEndpoint("mock:exceptionSetting", MockEndpoint.class);
        aEndpoint = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);

        exceptionSettingEndpoint.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new Exception("Throw me!"));
            }
        });

        Object lookedUpBean = context.getRegistry().lookupByName("myBean");
        assertSame("Lookup of 'myBean' should return same object!", myBean, lookedUpBean);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", myBean);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").doTry().routingSlip(header(ROUTING_SLIP_HEADER)).end().to("mock:noexception").doCatch(Exception.class).to("mock:exception");
            }
        };
    }

    public static class MyBean {
        public MyBean() {
        }

        public void throwException() throws Exception {
            throw new Exception("Throw me!");
        }
    }
}
