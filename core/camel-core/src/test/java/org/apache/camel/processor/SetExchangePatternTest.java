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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SetExchangePatternTest extends ContextTestSupport {

    @Test
    public void testInOut() throws Exception {
        assertMessageReceivedWithPattern("direct:testInOut", ExchangePattern.InOut);
    }

    @Test
    public void testInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:testInOnly", ExchangePattern.InOnly);
    }

    @Test
    public void testSetToInOnlyThenTo() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetToInOnlyThenTo", ExchangePattern.InOnly);
    }

    @Test
    public void testSetToInOutThenTo() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetToInOutThenTo", ExchangePattern.InOut);
    }

    @Test
    public void testToWithInOnlyParam() throws Exception {
        assertMessageReceivedWithPattern("direct:testToWithInOnlyParam", ExchangePattern.InOnly);
    }

    @Test
    public void testToWithInOutParam() throws Exception {
        assertMessageReceivedWithPattern("direct:testToWithInOutParam", ExchangePattern.InOut);
    }

    @Test
    public void testSetExchangePatternInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetExchangePatternInOnly", ExchangePattern.InOnly);
    }

    @Test
    public void testPreserveOldMEPInOut() throws Exception {
        // the mock should get an InOut MEP
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOut);

        // we send an InOnly
        Exchange out = template.send("direct:testInOut", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.setPattern(ExchangePattern.InOnly);
            }
        });

        // the MEP should be preserved
        assertNotNull(out);
        assertEquals(ExchangePattern.InOnly, out.getPattern());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPreserveOldMEPInOnly() throws Exception {
        // the mock should get an InOnly MEP
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        // we send an InOut
        Exchange out = template.send("direct:testInOnly", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.setPattern(ExchangePattern.InOut);
            }
        });

        // the MEP should be preserved
        assertNotNull(out);
        assertEquals(ExchangePattern.InOut, out.getPattern());

        assertMockEndpointsSatisfied();
    }

    protected void assertMessageReceivedWithPattern(String sendUri, ExchangePattern expectedPattern) throws InterruptedException {
        ExchangePattern sendPattern;
        switch (expectedPattern) {
            case InOut:
                sendPattern = ExchangePattern.InOnly;
                break;
            case InOnly:
                sendPattern = ExchangePattern.InOut;
                break;
            default:
                sendPattern = ExchangePattern.InOnly;
        }

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        String expectedBody = "InOnlyMessage";
        resultEndpoint.expectedBodiesReceived(expectedBody);
        resultEndpoint.expectedHeaderReceived("foo", "bar");

        template.sendBodyAndHeader(sendUri, sendPattern, expectedBody, "foo", "bar");
        resultEndpoint.assertIsSatisfied();
        ExchangePattern actualPattern = resultEndpoint.getExchanges().get(0).getPattern();
        assertEquals("received exchange pattern", actualPattern, expectedPattern);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                // Send to an endpoint using InOut
                from("direct:testInOut").inOut("mock:result");

                // Send to an endpoint using InOut
                from("direct:testInOnly").inOnly("mock:result");

                // Set the exchange pattern to InOut, then send it from
                // direct:inOnly to mock:result endpoint
                from("direct:testSetToInOnlyThenTo").setExchangePattern(ExchangePattern.InOnly).to("mock:result");
                from("direct:testSetToInOutThenTo").setExchangePattern(ExchangePattern.InOut).to("mock:result");

                // Or we can pass the pattern as a parameter to the to() method
                from("direct:testToWithInOnlyParam").to(ExchangePattern.InOnly, "mock:result");
                from("direct:testToWithInOutParam").to(ExchangePattern.InOut, "mock:result");

                // Set the exchange pattern to InOut, then send it on
                from("direct:testSetExchangePatternInOnly").setExchangePattern(ExchangePattern.InOnly).to("mock:result");
                // END SNIPPET: example
            }
        };
    }

}
