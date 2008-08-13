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
package org.apache.camel.component.mock;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class MockEndpointTest extends ContextTestSupport {

    public void testAscendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    public void testAscendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 15, 14);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testDescendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 12, 11);

        resultEndpoint.assertIsSatisfied();
    }

    public void testDescendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 11, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testExpectsBodiesInOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectedBodiesReceived(listOfMessages(11, 12, 13, 14, 15));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }    

    public void testExpectsBodiesInAnyOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectedBodiesReceivedInAnyOrder(listOfMessages(11, 12, 13, 14, 15));

        sendMessages(15, 12, 14, 13, 11);

        resultEndpoint.assertIsSatisfied();
    }       
    
    public void testNoDuplicateMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    public void testDuplicateMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testExpectationsAfterMessagesArrivePass() throws Exception {
        sendMessages(11, 12, 13, 14, 12);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectedMessageCount(5);
        resultEndpoint.assertIsNotSatisfied();
    }

    public void testExpectationsAfterMessagesArriveFail() throws Exception {
        sendMessages(11, 12, 13, 14, 12);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(6);
        resultEndpoint.assertIsNotSatisfied();
    }

    public void testReset() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result"); 
        resultEndpoint.expectedMessageCount(2);

        sendMessages(11, 12);

        resultEndpoint.assertIsSatisfied();
        resultEndpoint.reset();

        resultEndpoint.expectedMessageCount(3);

        sendMessages(11, 12, 13);

        resultEndpoint.assertIsSatisfied();
    }

    public void testExpectationOfHeader() throws InterruptedException {        
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();
        
        // assert header & value are same
        resultEndpoint.expectedHeaderReceived("header", "value");
        sendHeader("header", "value");
        resultEndpoint.assertIsSatisfied();
        
        resultEndpoint.reset();
        
        // assert failure when value is different
        resultEndpoint.expectedHeaderReceived("header", "value1");
        sendHeader("header", "value");
        resultEndpoint.assertIsNotSatisfied();       

        resultEndpoint.reset();

        // assert failure when header name is different       
        resultEndpoint.expectedHeaderReceived("header1", "value");
        sendHeader("header", "value");
        resultEndpoint.assertIsNotSatisfied();               
        
        resultEndpoint.reset();
        
        // assert failure when both header name & value are different
        resultEndpoint.expectedHeaderReceived("header1", "value1");
        sendHeader("header", "value");
        resultEndpoint.assertIsNotSatisfied();                       
    }
    
    protected void sendMessages(int... counters) {
        for (int counter : counters) {
            template.sendBodyAndHeader("direct:a", createTestMessage(counter),
                    "counter", counter);
        }
    }

    private String createTestMessage(int counter) {
        return "<message>" + counter + "</message>";
    }

    protected List<String> listOfMessages(int... counters) {
        List<String> list = new ArrayList<String>(counters.length);
        for (int counter : counters) {
            list.add(createTestMessage(counter));
        }
        return list;
    }   
    
    protected void sendHeader(String name, String value) {
        template.sendBodyAndHeader("direct:a", "body", name, value);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to("mock:result");
            }
        };
    }

}
