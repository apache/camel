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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
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

    public void testExpectsBodiesInAnyOrderWithDuplicates() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceivedInAnyOrder(listOfMessages(11, 15, 12, 12, 13, 14, 15, 15));

        sendMessages(15, 15, 12, 14, 13, 12, 15, 11);

        resultEndpoint.assertIsSatisfied();
    }

    public void testExpectsHeadersInAnyOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedHeaderValuesReceivedInAnyOrder("counter", 11, 12, 13, 14, 15);

        sendMessages(15, 12, 14, 13, 11);

        resultEndpoint.assertIsSatisfied();
    }

    public void testExpectsHeadersInAnyOrderFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedHeaderValuesReceivedInAnyOrder("counter", 11, 12, 7, 14, 15);

        sendMessages(15, 12, 14, 13, 11);

        try {
            resultEndpoint.assertIsSatisfied();
            fail("Should fail");
        } catch (AssertionError e) {
            assertEquals("mock://result Expected 5 headers with key[counter], received 4 headers. Expected header values: [7]", e.getMessage());
        }
    }

    public void testExpectsPropertiesInAnyOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedPropertyValuesReceivedInAnyOrder("foo", 123, 456);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 456);
            }
        });

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
            }
        });

        resultEndpoint.assertIsSatisfied();
    }

    public void testExpectsPropertiesInAnyOrderFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedPropertyValuesReceivedInAnyOrder("foo", 123, 456);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
            }
        });

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 789);
            }
        });

        try {
            resultEndpoint.assertIsSatisfied();
            fail("Should fail");
        } catch (AssertionError e) {
            assertEquals("mock://result Expected 2 properties with key[foo], received 1 properties. Expected property values: [456]", e.getMessage());
        }
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
        resultEndpoint.assertIsSatisfied();
    }

    public void testExpectationsAfterMessagesArriveFail() throws Exception {
        sendMessages(11, 12, 13, 14, 12);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(6);
        // wait at most 0.5 sec to speedup unit testing
        resultEndpoint.setResultWaitTime(500);

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
    }

    public void testExpressionExpectationOfHeader() throws InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();

        resultEndpoint.expectedHeaderReceived("number", 123);
        template.sendBodyAndHeader("direct:a", "<foo><id>123</id></foo>", "number", XPathBuilder.xpath("/foo/id", Integer.class));
        resultEndpoint.assertIsSatisfied();
    }    
    
    public void testExpressionExpectationOfProperty() throws InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();

        resultEndpoint.expectedPropertyReceived("number", 123);
        template.sendBodyAndProperty("direct:a", "<foo><id>123</id></foo>", "number", XPathBuilder.xpath("/foo/id", Integer.class));
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
            fail("Should have thrown an exception");
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
            fail("Should have thrown an exception");
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
            fail("Should have thrown an exception");
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
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    public void testMinimumCount() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMinimumMessageCount(2);

        sendMessages(3, 4, 5);

        mock.assertIsSatisfied();

        assertEquals(2, mock.getExpectedMinimumCount());
    }

    public void testResolve() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(100);

        template.sendBody("direct:a", "Hello World");

        // should only be 1 message
        mock.assertIsNotSatisfied();
        assertEquals(100, mock.getResultWaitTime());
    }

    public void testResolveTimeout() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(100);

        mock.assertIsNotSatisfied(500);

        assertEquals(2, mock.getExpectedCount());
        assertEquals(100, mock.getResultWaitTime());
    }

    public void testSleepForEmptyTest() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);
        
        mock.assertIsSatisfied();

        assertEquals(0, mock.getExpectedCount());
        assertEquals(100, mock.getSleepForEmptyTest());
    }

    public void testSleepForEmptyTestAssert() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(0);

        mock.assertIsSatisfied(100);

        assertEquals(0, mock.getExpectedCount());
        assertEquals(0, mock.getSleepForEmptyTest());
        assertEquals(0, mock.getResultWaitTime());
    }

    public void testReporter() throws Exception {
        final AtomicBoolean reported = new AtomicBoolean(false);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setExpectedMessageCount(1);
        mock.setReporter(new Processor() {
            public void process(Exchange exchange) throws Exception {
                reported.set(true);
            }
        });

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();

        assertNotNull(mock.getReporter());
        assertTrue(reported.get());
    }

    public void testNoArgCtr() {
        MockEndpoint mock = new MockEndpoint();
        mock.setCamelContext(context);
        mock.setEndpointUriIfNotSpecified("mock:bar");
        try {
            mock.createConsumer(null);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // not possible
        }

        assertEquals(0, mock.getFailures().size());
    }

    public void testHeaderMissing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");
        
        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            assertEquals("mock://result No header with name bar found for message: 0", e.getMessage());
        }
    }

    public void testHeaderInvalidValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("bar", "cheese");

        template.sendBodyAndHeader("direct:a", "Hello World", "bar", "beer");

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            assertEquals("mock://result Header with name bar for message: 0. Expected: <cheese> but was: <beer>", e.getMessage());
        }
    }
    
    public void testPropertyMissing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "cheese");

        template.sendBodyAndProperty("direct:a", "Hello World", "foo", 123);

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            assertEquals("mock://result No property with name bar found for message: 0", e.getMessage());
        }
    }
    
    public void testPropertyExpectedNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", null);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
            }
        });

        mock.assertIsNotSatisfied();

        resetMocks();

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", null);
            }
        });

        mock.assertIsSatisfied();

        resetMocks();

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // no foo property
            }
        });

        mock.assertIsSatisfied();
    }

    public void testPropertyInvalidValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("bar", "cheese");

        template.sendBodyAndProperty("direct:a", "Hello World", "bar", "beer");

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            assertEquals("mock://result Property with name bar for message: 0. Expected: <cheese> but was: <beer>", e.getMessage());
        }
    }

    public void testMessageIndexIsEqualTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isEqualTo(123);
        mock.message(1).header("bar").isEqualTo(444);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) == 444 evaluated as: 234 == 444";
            assertTrue(e.getMessage().startsWith(s));
        }
    }

    public void testPredicateEvaluationIsNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isNotNull();
        mock.message(1).header("bar").isNull();

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) is null evaluated as: 234 is null";
            assertTrue(e.getMessage().startsWith(s));
        }
    }

    public void testPredicateEvaluationIsInstanceOf() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isNotNull();
        mock.message(1).header("bar").isInstanceOf(String.class);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        try {
            assertMockEndpointsSatisfied();
            fail("Should have thrown exception");
        } catch (AssertionError e) {
            String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) instanceof java.lang.String";
            assertTrue(e.getMessage().startsWith(s));
        }
    }

    public void testExchangePattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);
        mock.message(1).exchangePattern().isEqualTo(ExchangePattern.InOut);

        template.sendBody("direct:a", "Hello World");
        template.requestBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testExpectedExchangePattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedExchangePattern(ExchangePattern.InOnly);

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();

        // reset and try with InOut this time
        resetMocks();
        mock.expectedMessageCount(1);
        mock.expectedExchangePattern(ExchangePattern.InOut);

        template.requestBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testSetMultipleExpectedHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedHeaders2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedHeaders3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", null);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }
    
    public void testSetMultipleExpectedHeaders4() throws Exception {
        // to test the header value with Stream which can only be consumed once
        InputStream is = new ByteArrayInputStream("Test".getBytes());
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "Test");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", is);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedHeadersShouldFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 456);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    public void testSetMultipleExpectedHeadersShouldFail2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", "wine");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    public void testSetMultipleExpectedHeadersShouldFail3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        map = new HashMap<String, Object>();
        map.put("foo", 123);
        map.put("bar", "wine");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    public void testSetMultipleExpectedProperties() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "beer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "beer");
            }
        });

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedProperties2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "beer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "beer");
            }
        });

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "beer");
            }
        });

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedProperties3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", null);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
            }
        });

        mock.assertIsSatisfied();
    }

    public void testSetMultipleExpectedPropertiesShouldFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "beer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 456);
                exchange.setProperty("bar", "beer");
            }
        });

        mock.assertIsNotSatisfied();
    }

    public void testSetMultipleExpectedPropertiesShouldFail2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "beer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "wine");
            }
        });

        mock.assertIsNotSatisfied();
    }

    public void testSetMultipleExpectedPropertiesShouldFail3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "beer");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "beer");
            }
        });

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty("foo", 123);
                exchange.setProperty("bar", "wine");
            }
        });

        mock.assertIsNotSatisfied();
    }

    public void testExpectedBodyTypeCoerce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(987);

        // start with 0 (zero) to have it converted to the number and match 987
        template.sendBody("direct:a", "0987");

        assertMockEndpointsSatisfied();
    }

    public void testExpectedBodyExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(987);

        // start with 0 (zero) to have it converted to the number and match 987
        // and since its an expression it would be evaluated first as well
        template.sendBody("direct:a", ExpressionBuilder.constantExpression("0987"));

        assertMockEndpointsSatisfied();
    }

    public void testResetDefaultProcessor() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                counter.incrementAndGet();
            }
        });
        mock.expectedMessageCount(1);
        sendMessages(1);
        mock.assertIsSatisfied();
        assertEquals(1, counter.get());

        resetMocks();
        mock.expectedMessageCount(1);
        sendMessages(1);
        mock.assertIsSatisfied();
        // counter should not be changed this time
        assertEquals(1, counter.get());
    }
    
    public void testRetainFirst() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainFirst(5);
        mock.expectedMessageCount(10);
        
        sendMessages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertMockEndpointsSatisfied();

        assertEquals(10, mock.getReceivedCounter());
        assertEquals(5, mock.getExchanges().size());
        assertEquals(5, mock.getReceivedExchanges().size());

        assertEquals("<message>0</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>1</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>2</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>3</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>4</message>", mock.getReceivedExchanges().get(4).getIn().getBody());
    }

    public void testRetainLast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainLast(5);
        mock.expectedMessageCount(10);

        sendMessages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertMockEndpointsSatisfied();

        assertEquals(10, mock.getReceivedCounter());
        assertEquals(5, mock.getExchanges().size());
        assertEquals(5, mock.getReceivedExchanges().size());

        assertEquals("<message>5</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>6</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>7</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>8</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>9</message>", mock.getReceivedExchanges().get(4).getIn().getBody());
    }

    public void testRetainFirstAndLast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainFirst(5);
        mock.setRetainLast(5);
        mock.expectedMessageCount(20);

        sendMessages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);

        assertMockEndpointsSatisfied();

        assertEquals(20, mock.getReceivedCounter());
        assertEquals(10, mock.getExchanges().size());
        assertEquals(10, mock.getReceivedExchanges().size());

        assertEquals("<message>0</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>1</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>2</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>3</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>4</message>", mock.getReceivedExchanges().get(4).getIn().getBody());

        assertEquals("<message>15</message>", mock.getReceivedExchanges().get(5).getIn().getBody());
        assertEquals("<message>16</message>", mock.getReceivedExchanges().get(6).getIn().getBody());
        assertEquals("<message>17</message>", mock.getReceivedExchanges().get(7).getIn().getBody());
        assertEquals("<message>18</message>", mock.getReceivedExchanges().get(8).getIn().getBody());
        assertEquals("<message>19</message>", mock.getReceivedExchanges().get(9).getIn().getBody());
    }

    public void testRetainFirstAndLastOverlap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainFirst(5);
        mock.setRetainLast(5);
        mock.expectedMessageCount(8);

        sendMessages(0, 1, 2, 3, 4, 5, 6, 7);

        assertMockEndpointsSatisfied();

        assertEquals(8, mock.getReceivedCounter());
        assertEquals(8, mock.getExchanges().size());
        assertEquals(8, mock.getReceivedExchanges().size());

        assertEquals("<message>0</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>1</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>2</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>3</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>4</message>", mock.getReceivedExchanges().get(4).getIn().getBody());
        assertEquals("<message>5</message>", mock.getReceivedExchanges().get(5).getIn().getBody());
        assertEquals("<message>6</message>", mock.getReceivedExchanges().get(6).getIn().getBody());
        assertEquals("<message>7</message>", mock.getReceivedExchanges().get(7).getIn().getBody());
    }

    public void testRetainFirstAndLastNoGap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainFirst(5);
        mock.setRetainLast(5);
        mock.expectedMessageCount(10);

        sendMessages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertMockEndpointsSatisfied();

        assertEquals(10, mock.getReceivedCounter());
        assertEquals(10, mock.getExchanges().size());
        assertEquals(10, mock.getReceivedExchanges().size());

        assertEquals("<message>0</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>1</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>2</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>3</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>4</message>", mock.getReceivedExchanges().get(4).getIn().getBody());
        assertEquals("<message>5</message>", mock.getReceivedExchanges().get(5).getIn().getBody());
        assertEquals("<message>6</message>", mock.getReceivedExchanges().get(6).getIn().getBody());
        assertEquals("<message>7</message>", mock.getReceivedExchanges().get(7).getIn().getBody());
        assertEquals("<message>8</message>", mock.getReceivedExchanges().get(8).getIn().getBody());
        assertEquals("<message>9</message>", mock.getReceivedExchanges().get(9).getIn().getBody());
    }

    public void testRetainFirstAndLastSingleGap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setRetainFirst(5);
        mock.setRetainLast(5);
        mock.expectedMessageCount(11);

        sendMessages(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertMockEndpointsSatisfied();

        assertEquals(11, mock.getReceivedCounter());
        assertEquals(10, mock.getExchanges().size());
        assertEquals(10, mock.getReceivedExchanges().size());

        assertEquals("<message>0</message>", mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("<message>1</message>", mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals("<message>2</message>", mock.getReceivedExchanges().get(2).getIn().getBody());
        assertEquals("<message>3</message>", mock.getReceivedExchanges().get(3).getIn().getBody());
        assertEquals("<message>4</message>", mock.getReceivedExchanges().get(4).getIn().getBody());
        assertEquals("<message>6</message>", mock.getReceivedExchanges().get(5).getIn().getBody());
        assertEquals("<message>7</message>", mock.getReceivedExchanges().get(6).getIn().getBody());
        assertEquals("<message>8</message>", mock.getReceivedExchanges().get(7).getIn().getBody());
        assertEquals("<message>9</message>", mock.getReceivedExchanges().get(8).getIn().getBody());
        assertEquals("<message>10</message>", mock.getReceivedExchanges().get(9).getIn().getBody());
    }

    protected void sendMessages(int... counters) {
        for (int counter : counters) {
            template.sendBodyAndHeader("direct:a", createTestMessage(counter), "counter", counter);
        }
    }

    private String createTestMessage(int counter) {
        return "<message>" + counter + "</message>";
    }

    protected Object[] listOfMessages(int... counters) {
        List<String> list = new ArrayList<String>(counters.length);
        for (int counter : counters) {
            list.add(createTestMessage(counter));
        }
        return list.toArray();
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
