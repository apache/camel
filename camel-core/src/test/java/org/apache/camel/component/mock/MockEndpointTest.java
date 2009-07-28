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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.impl.JndiRegistry;

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
        // wait at most 2 sec to speedup unit testing 
        resultEndpoint.setResultWaitTime(2000);
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
    
    public void testExpectationOfHeaderWithNumber() throws InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();

        // assert we can assert using other than string, eg numbers
        resultEndpoint.expectedHeaderReceived("number", 123);
        sendHeader("number", 123);
        resultEndpoint.assertIsSatisfied();

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testExpressionExpectationOfHeader() throws InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();

        resultEndpoint.expectedHeaderReceived("number", 123);
        template.sendBodyAndHeader("direct:a", "<foo><id>123</id></foo>", "number", XPathBuilder.xpath("/foo/id", Integer.class));
        resultEndpoint.assertIsSatisfied();
    }    
    
    public void testAscending() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsAscending().body();
        mock.expectsAscending().header("counter");
        sendMessages(1, 2, 3, 4, 5);

        assertMockEndpointsSatisfied();
    }

    public void testAscendingFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsAscending().body();
        mock.expectsAscending().header("counter");
        sendMessages(1, 2, 5, 3, 4);

        mock.assertIsNotSatisfied();
    }

    public void testDescending() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsDescending().body();
        mock.expectsDescending().header("counter");
        sendMessages(5, 4, 3, 2, 1);

        assertMockEndpointsSatisfied();
    }

    public void testDescendingFaied() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsDescending().body();
        mock.expectsDescending().header("counter");
        sendMessages(5, 4, 2, 3, 1);

        mock.assertIsNotSatisfied();
    }

    public void testNoDuplicates() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsNoDuplicates().body();
        mock.expectsNoDuplicates().header("counter");
        sendMessages(1, 2, 3, 4, 5);

        assertMockEndpointsSatisfied();
    }

    public void testNoDuplicatesFaied() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsNoDuplicates().body();
        mock.expectsNoDuplicates().header("counter");
        sendMessages(1, 2, 5, 2, 4);

        mock.assertIsNotSatisfied();
    }

    public void testBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("<message>1</message>");
        sendMessages(1);

        mock.assertIsSatisfied();
    }

    public void testBodyTransformed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().method("foo", "greet");
        template.sendBody("direct:b", "Hello");

        mock.assertIsSatisfied();
    }

    public void testBodyFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("<message>2</message>");
        sendMessages(1);

        mock.assertIsNotSatisfied();
    }

    public void testSimulateError() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Forced"));
            }
        });

        try {
            template.sendBody("direct:a", "Hello World");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    public void testSimulateErrorByThrowingException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Forced");
            }
        });

        try {
            template.sendBody("direct:a", "Hello World");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    public void testSimulateErrorWithIndex() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.whenExchangeReceived(2, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Forced"));
            }
        });

        template.sendBody("direct:a", "Hello World");
        try {
            template.sendBody("direct:a", "Hello World");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    public void testSimulateErrorWithIndexByThrowingException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.whenExchangeReceived(2, new Processor() {
            public void process(Exchange exchange) throws Exception {
                throw new IllegalArgumentException("Forced");
            }
        });

        template.sendBody("direct:a", "Hello World");
        try {
            template.sendBody("direct:a", "Bye World");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    protected void sendMessages(int... counters) {
        for (int counter : counters) {
            template.sendBodyAndHeader("direct:a", createTestMessage(counter), "counter", counter);
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
    
    protected void sendHeader(String name, Object value) {
        template.sendBodyAndHeader("direct:a", "body", name, value);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyHelloBean());
        return jndi;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to("mock:result");

                from("direct:b").transform(body().append(" World")).to("mock:result");
            }
        };
    }

    public static final class MyHelloBean {

        public String greet() {
            return "Hello World";
        }
    }
}
