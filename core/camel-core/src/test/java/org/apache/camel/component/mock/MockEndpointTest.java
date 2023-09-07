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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MockEndpointTest extends ContextTestSupport {

    @Test
    public void testAscendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAscendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 15, 14);

        resultEndpoint.assertIsNotSatisfied();
    }

    @Test
    public void testDescendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 12, 11);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDescendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 11, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    @Test
    public void testExpectsBodiesInOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived(listOfMessages(11, 12, 13, 14, 15));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExpectsBodiesInAnyOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceivedInAnyOrder(listOfMessages(11, 12, 13, 14, 15));

        sendMessages(15, 12, 14, 13, 11);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExpectsBodiesInAnyOrderWithDuplicates() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceivedInAnyOrder(listOfMessages(11, 15, 12, 12, 13, 14, 15, 15));

        sendMessages(15, 15, 12, 14, 13, 12, 15, 11);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExpectsHeadersInAnyOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedHeaderValuesReceivedInAnyOrder("counter", 11, 12, 13, 14, 15);

        sendMessages(15, 12, 14, 13, 11);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExpectsHeadersInAnyOrderFail() {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedHeaderValuesReceivedInAnyOrder("counter", 11, 12, 7, 14, 15);

        sendMessages(15, 12, 14, 13, 11);

        AssertionError e = assertThrows(AssertionError.class,
                resultEndpoint::assertIsSatisfied,
                "Should fail");

        assertEquals("mock://result Expected 5 headers with key[counter], received 4 headers. Expected header values: [7]",
                e.getMessage());
    }

    @Test
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

    @Test
    public void testExpectsPropertiesInAnyOrderFail() {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedPropertyValuesReceivedInAnyOrder("foo", 123, 456);

        template.send("direct:a", exchange -> exchange.setProperty("foo", 123));
        template.send("direct:a", exchange -> exchange.setProperty("foo", 789));

        AssertionError e = assertThrows(AssertionError.class,
                resultEndpoint::assertIsSatisfied,
                "Should fail");

        assertEquals(
                "mock://result Expected 2 properties with key[foo], received 1 properties. Expected property values: [456]",
                e.getMessage());
    }

    @Test
    public void testNoDuplicateMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testDuplicateMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    @Test
    public void testExpectationsAfterMessagesArrivePass() throws Exception {
        sendMessages(11, 12, 13, 14, 12);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(5);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testExpectationsAfterMessagesArriveFail() throws Exception {
        sendMessages(11, 12, 13, 14, 12);

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(6);
        // wait at most 0.5 sec to speedup unit testing
        resultEndpoint.setResultWaitTime(500);

        resultEndpoint.assertIsNotSatisfied();
    }

    @Test
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

    @Test
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

    @Test
    public void testExpectationOfHeaderWithNumber() throws InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.reset();

        // assert we can assert using other than string, eg numbers
        resultEndpoint.expectedHeaderReceived("number", 123);
        sendHeader("number", 123);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAscending() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsAscending().body();
        mock.expectsAscending().header("counter");
        sendMessages(1, 2, 3, 4, 5);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAscendingFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsAscending().body();
        mock.expectsAscending().header("counter");
        sendMessages(1, 2, 5, 3, 4);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testDescending() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsDescending().body();
        mock.expectsDescending().header("counter");
        sendMessages(5, 4, 3, 2, 1);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDescendingFaied() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsDescending().body();
        mock.expectsDescending().header("counter");
        sendMessages(5, 4, 2, 3, 1);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testNoDuplicates() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsNoDuplicates().body();
        mock.expectsNoDuplicates().header("counter");
        sendMessages(1, 2, 3, 4, 5);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoDuplicatesFaied() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsNoDuplicates().body();
        mock.expectsNoDuplicates().header("counter");
        sendMessages(1, 2, 5, 2, 4);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("<message>1</message>");
        sendMessages(1);

        mock.assertIsSatisfied();
    }

    @Test
    public void testBodyTransformed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().method("foo", "greet");
        template.sendBody("direct:b", "Hello");

        mock.assertIsSatisfied();
    }

    @Test
    public void testBodyFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodyReceived().constant("<message>2</message>");
        sendMessages(1);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testSimulateError() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.whenAnyExchangeReceived(exchange -> exchange.setException(new IllegalArgumentException("Forced")));

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:a", "Hello World"),
                "Should have thrown an exception");

        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced", e.getCause().getMessage());
    }

    @Test
    public void testSimulateErrorByThrowingException() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.whenAnyExchangeReceived(exchange -> {
            throw new IllegalArgumentException("Forced");
        });

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:a", "Hello World"),
                "Should have thrown an exception");

        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced", e.getCause().getMessage());
    }

    @Test
    public void testSimulateErrorWithIndex() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.whenExchangeReceived(2, exchange -> exchange.setException(new IllegalArgumentException("Forced")));

        template.sendBody("direct:a", "Hello World");

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:a", "Hello World"),
                "Should have thrown an exception");

        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced", e.getCause().getMessage());
    }

    @Test
    public void testSimulateErrorWithIndexByThrowingException() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.whenExchangeReceived(2, exchange -> {
            throw new IllegalArgumentException("Forced");
        });

        template.sendBody("direct:a", "Hello World");
        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:a", "Bye World"),
                "Should have thrown an exception");

        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Forced", e.getCause().getMessage());
    }

    @Test
    public void testMinimumCount() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMinimumMessageCount(2);

        sendMessages(3, 4, 5);

        mock.assertIsSatisfied();

        assertEquals(2, mock.getExpectedMinimumCount());
    }

    @Test
    public void testResolve() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(100);

        template.sendBody("direct:a", "Hello World");

        // should only be 1 message
        mock.assertIsNotSatisfied();
        assertEquals(100, mock.getResultWaitTime());
    }

    @Test
    public void testResolveTimeout() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(100);

        mock.assertIsNotSatisfied(500);

        assertEquals(2, mock.getExpectedCount());
        assertEquals(100, mock.getResultWaitTime());
    }

    @Test
    public void testSleepForEmptyTest() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(0);
        mock.setSleepForEmptyTest(100);

        mock.assertIsSatisfied();

        assertEquals(0, mock.getExpectedCount());
        assertEquals(100, mock.getSleepForEmptyTest());
    }

    @Test
    public void testSleepForEmptyTestAssert() throws Exception {
        MockEndpoint mock = MockEndpoint.resolve(context, "mock:result");
        mock.expectedMessageCount(0);

        mock.assertIsSatisfied(100);

        assertEquals(0, mock.getExpectedCount());
        assertEquals(0, mock.getSleepForEmptyTest());
        assertEquals(0, mock.getResultWaitTime());
    }

    @Test
    public void testReporter() throws Exception {
        final AtomicBoolean reported = new AtomicBoolean();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setExpectedMessageCount(1);
        mock.setReporter(exchange -> reported.set(true));

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();

        assertNotNull(mock.getReporter());
        assertTrue(reported.get());
    }

    @Test
    public void testNoArgCtr() {
        MockEndpoint mock = new MockEndpoint("mock:bar", new MockComponent(context));
        assertThrows(Exception.class,
                () -> mock.createConsumer(null),
                "Should have thrown an exception");

        assertEquals(0, mock.getFailures().size());
    }

    @Test
    public void testHeaderMissing() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "cheese");

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");

        assertEquals("mock://result No header with name bar found for message: 0", e.getMessage());
    }

    @Test
    public void testHeaderNoMessageSent() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived("foo", 123);
        // just wait a little bit as we dont want to wait 10 seconds (default)
        mock.setResultWaitTime(5);

        // do not send any message
        AssertionError e = assertThrows(AssertionError.class,
                mock::assertIsSatisfied,
                "Should fail");

        assertEquals("mock://result Received message count 0, expected at least 1", e.getMessage());
    }

    @Test
    public void testHeaderInvalidValue() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("bar", "cheese");

        template.sendBodyAndHeader("direct:a", "Hello World", "bar", "beer");

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");

        assertEquals("mock://result Header with name bar for message: 0. Expected: <cheese> but was: <beer>",
                e.getMessage());
    }

    @Test
    public void testPropertyMissing() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("foo", 123);
        mock.expectedPropertyReceived("bar", "cheese");

        template.sendBodyAndProperty("direct:a", "Hello World", "foo", 123);

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");

        assertEquals("mock://result No property with name bar found for message: 0", e.getMessage());
    }

    @Test
    public void testPropertyExpectedNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty("foo").isNull();

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

    @Test
    public void testPropertyInvalidValue() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived("bar", "cheese");

        template.sendBodyAndProperty("direct:a", "Hello World", "bar", "beer");

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");
        assertEquals("mock://result Property with name bar for message: 0. Expected: <cheese> but was: <beer>",
                e.getMessage());
    }

    @Test
    public void testMessageIndexIsEqualTo() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isEqualTo(123);
        mock.message(1).header("bar").isEqualTo(444);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");

        String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) == 444 evaluated as: 234 == 444";
        assertTrue(e.getMessage().startsWith(s));
    }

    @Test
    public void testPredicateEvaluationIsNull() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isNotNull();
        mock.message(1).header("bar").isNull();

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");

        String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) is null evaluated as: 234 is null";
        assertTrue(e.getMessage().startsWith(s));
    }

    @Test
    public void testPredicateEvaluationIsInstanceOf() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").isNotNull();
        mock.message(1).header("bar").isInstanceOf(String.class);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Hello World", "bar", 234);

        AssertionError e = assertThrows(AssertionError.class,
                this::assertMockEndpointsSatisfied,
                "Should have thrown exception");
        String s = "Assertion error at index 1 on mock mock://result with predicate: header(bar) instanceof java.lang.String";
        assertTrue(e.getMessage().startsWith(s));
    }

    @Test
    public void testExchangePattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);
        mock.message(1).exchangePattern().isEqualTo(ExchangePattern.InOut);

        template.sendBody("direct:a", "Hello World");
        template.requestBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotExchangePattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).exchangePattern().isEqualTo(ExchangePattern.InOnly);
        mock.message(1).exchangePattern().isEqualTo(ExchangePattern.InOnly);

        template.sendBody("direct:a", "Hello World");
        template.requestBody("direct:a", "Bye World");

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testBodyPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).body().matches().constant("Hello World");
        mock.message(1).body().matches().constant("Bye World");

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Bye World", "bar", 234);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotBodyPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).body().matches().constant("Hello World");
        mock.message(1).body().matches().constant("Hi World");

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Bye World", "bar", 234);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testHeaderPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").matches().constant(123);
        mock.message(1).header("bar").matches().constant(234);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Bye World", "bar", 234);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotHeaderPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header("foo").matches().constant(123);
        mock.message(1).header("bar").matches().constant(666);

        template.sendBodyAndHeader("direct:a", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:a", "Bye World", "bar", 234);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testExpectedExchangePattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedExchangePattern(ExchangePattern.InOnly);

        template.sendBody("direct:a", "Hello World");
        template.sendBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();

        // reset and try with InOut this time
        resetMocks();
        mock.expectedMessageCount(1);
        mock.expectedExchangePattern(ExchangePattern.InOut);

        template.requestBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeaders2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeaders3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", null);

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeaders4() throws Exception {
        // to test the header value with Stream which can only be consumed once
        InputStream is = new ByteArrayInputStream("Test".getBytes());
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "Test");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", is);
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeadersShouldFail() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 456);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeadersShouldFail2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", "wine");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    @Test
    public void testSetMultipleExpectedHeadersShouldFail3() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedHeaderReceived("foo", 123);
        mock.expectedHeaderReceived("bar", "beer");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", "beer");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        map = new HashMap<>();
        map.put("foo", 123);
        map.put("bar", "wine");
        template.sendBodyAndHeaders("direct:a", "Hello World", map);

        mock.assertIsNotSatisfied();
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testExpectedBodyTypeCoerce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(987);

        // start with 0 (zero) to have it converted to the number and match 987
        template.sendBody("direct:a", "0987");

        assertMockEndpointsSatisfied();
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testExpectedMessagesMatches() throws Exception {
        Language sl = context.resolveLanguage("simple");
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessagesMatches(sl.createPredicate("${body} == 'abc'"));
        template.sendBody("direct:a", "abc");
        mock.assertIsSatisfied();

        mock.reset();

        mock.expectedMessagesMatches(sl.createPredicate("${body} == 'abc'"));
        template.sendBody("direct:a", "def");
        mock.assertIsNotSatisfied();
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
        List<String> list = new ArrayList<>(counters.length);
        for (int counter : counters) {
            list.add(createTestMessage(counter));
        }
        return list.toArray();
    }

    protected void sendHeader(String name, Object value) {
        template.sendBodyAndHeader("direct:a", "body", name, value);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyHelloBean());
        return jndi;
    }

    @Override
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
