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

    public void testInvokeWithInOut() throws Exception {
        assertMessageReceivedWithPattern("direct:invokeWithInOut", ExchangePattern.InOut);
    }

    public void testInvokeWithInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:invokeWithInOnly", ExchangePattern.InOnly);
    }

    public void testSetInOut() throws Exception {
        assertMessageReceivedWithPattern("direct:inOnly", ExchangePattern.InOut);
    }

    public void testSetInOutAsToParam() throws Exception {
        assertMessageReceivedWithPattern("direct:inOnlyAsToParam", ExchangePattern.InOnly);
    }

    public void testSetInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:inOut", ExchangePattern.InOnly);
    }
    
    public void testSetRobustInOnly() throws Exception {
        assertMessageReceivedWithPattern("direct:inOut1", ExchangePattern.RobustInOnly);
    }
    
    public void testSetInOnly2() throws Exception {
        assertMessageReceivedWithPattern("direct:inOut2", ExchangePattern.InOnly);
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
                from("direct:invokeWithInOut").inOut("mock:result");
                // Send to an endpoint using InOut
                from("direct:invokeWithInOnly").inOnly("mock:result");
                // Set the exchange pattern to InOut, then send it from direct:inOnly to mock:result endpoint
                from("direct:inOnly").inOut().to("mock:result");
                // Or we can pass the pattern as a parameter
                from("direct:inOnlyAsToParam").to(ExchangePattern.InOnly, "mock:result");
                // Set the exchange pattern to InOut, then send it from direct:inOut to mock:result endpoint
                from("direct:inOut").setExchangePattern(ExchangePattern.InOnly).to("mock:result");
                // Send the exchange from direct:inOut1 to mock:result with setting the exchange pattern to be RobustInOnly
                from("direct:inOut1").to(ExchangePattern.RobustInOnly, "mock:result");
                // Send the exchange from direct:inOut2 to mock:result with setting the exchange pattern to be InOnly
                from("direct:inOut2").inOnly("mock:result");
             // END SNIPPET: example   
            }
        };
    }

}
