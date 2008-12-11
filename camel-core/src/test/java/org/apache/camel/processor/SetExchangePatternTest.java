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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class SetExchangePatternTest extends ContextTestSupport {

    public void testInOut() throws Exception {
        assertMessageReceivedWithPattern("direct:testInOut", ExchangePattern.InOut);
    }

    public void testInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:testInOnly", ExchangePattern.InOnly);
    }

    public void testSetToInOnlyThenTo() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetToInOnlyThenTo", ExchangePattern.InOnly);
    }

    public void testSetToInOutThenTo() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetToInOutThenTo", ExchangePattern.InOut);
    }

    public void testToWithInOnlyParam() throws Exception {
        assertMessageReceivedWithPattern("direct:testToWithInOnlyParam", ExchangePattern.InOnly);
    }

    public void testToWithInOutParam() throws Exception {
        assertMessageReceivedWithPattern("direct:testToWithInOutParam", ExchangePattern.InOut);
    }

    public void testToWithRobustInOnlyParam() throws Exception {
        assertMessageReceivedWithPattern("direct:testToWithRobustInOnlyParam", ExchangePattern.RobustInOnly);
    }

    public void testSetExchangePatternInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:testSetExchangePatternInOnly", ExchangePattern.InOnly);
    }


    protected void assertMessageReceivedWithPattern(String sendUri, ExchangePattern expectedPattern) throws InterruptedException {
        ExchangePattern sendPattern;
        switch (expectedPattern) {
        case InOut:
            sendPattern = ExchangePattern.InOnly;
            break;
        case InOnly:
        case RobustInOnly:
            sendPattern = ExchangePattern.InOut;
            break;
        default:
            sendPattern = ExchangePattern.InOnly;
        }

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        String expectedBody = "InOnlyMessage";
        resultEndpoint.expectedBodiesReceived(expectedBody);
        template.sendBody(sendUri, sendPattern, expectedBody);
        resultEndpoint.assertIsSatisfied();
        ExchangePattern actualPattern = resultEndpoint.getExchanges().get(0).getPattern();
        assertEquals("received exchange pattern", actualPattern, expectedPattern);
    }


    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
             // START SNIPPET: example
                // Send to an endpoint using InOut
                from("direct:testInOut").inOut("mock:result");

                // Send to an endpoint using InOut
                from("direct:testInOnly").inOnly("mock:result");

                // Set the exchange pattern to InOut, then send it from direct:inOnly to mock:result endpoint
                from("direct:testSetToInOnlyThenTo").inOnly().to("mock:result");
                from("direct:testSetToInOutThenTo").inOut().to("mock:result");

                // Or we can pass the pattern as a parameter to the to() method
                from("direct:testToWithInOnlyParam").to(ExchangePattern.InOnly, "mock:result");
                from("direct:testToWithInOutParam").to(ExchangePattern.InOut, "mock:result");
                from("direct:testToWithRobustInOnlyParam").to(ExchangePattern.RobustInOnly, "mock:result");

                // Set the exchange pattern to InOut, then send it on
                from("direct:testSetExchangePatternInOnly").setExchangePattern(ExchangePattern.InOnly).to("mock:result");
             // END SNIPPET: example
            }
        };
    }

}
