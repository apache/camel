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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the Splitter stops because maxFailedRecords or errorThreshold is reached, the exchange keeps the original input
 * message and gets the exception, the same as with stopOnException, instead of the message of the failed split item.
 */
class SplitterThresholdStopKeepsOriginalMessageTest extends ContextTestSupport {

    private final RecordingAggregationStrategy stopStrategy = new RecordingAggregationStrategy();
    private final RecordingAggregationStrategy maxStrategy = new RecordingAggregationStrategy();

    private Exchange send(String uri, Object body) {
        return send(uri, body, false);
    }

    private Exchange send(String uri, Object body, boolean transacted) {
        return template.send(uri, e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader("orig", "yes");
            e.setProperty("origProp", "yes");
            e.getExchangeExtension().setTransacted(transacted);
        });
    }

    private static void assertOriginalKept(Exchange out, String body) {
        assertInstanceOf(IllegalStateException.class, out.getException().getCause());
        assertOriginalKept(out, (Object) body);
    }

    private static void assertOriginalKept(Exchange out, Object body) {
        assertInstanceOf(CamelExchangeException.class, out.getException());
        assertEquals(body, out.getMessage().getBody(), "body of the original message");
        assertEquals("yes", out.getMessage().getHeader("orig"));
        assertNull(out.getMessage().getHeader("item"), "header of the failed split item");
        assertNull(out.getProperty("itemProp"), "property of the failed split item");
        assertNull(out.getVariable("itemVar"), "variable of the failed split item");
        assertEquals("yes", out.getProperty("origProp"), "property removed in the failed split item");
    }

    @Test
    void stopOnExceptionKeepsOriginal() {
        assertOriginalKept(send("direct:stop", "a,b,c"), "a,b,c");
    }

    @Test
    void maxFailedRecordsKeepsOriginal() {
        Exchange out = send("direct:max", "a,b,c");
        assertOriginalKept(out, "a,b,c");
        assertTrue(out.getProperty(Exchange.SPLIT_RESULT, SplitResult.class).isAborted());
    }

    @Test
    void maxFailedRecordsAfterToleratedFailureKeepsOriginal() {
        Exchange out = send("direct:max2", "a,b,c");
        assertOriginalKept(out, "a,b,c");
        // the split result still has the index and the exception of each failed item
        SplitResult result = out.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertTrue(result.isAborted());
        assertEquals(List.of(0, 1), result.getFailures().stream().map(SplitResult.Failure::index).toList());
        assertInstanceOf(IllegalStateException.class, result.getFailures().get(1).exception());
    }

    @Test
    void shareUnitOfWorkMaxFailedRecordsKeepsOriginal() {
        assertOriginalKept(send("direct:share", "a,b,c"), "a,b,c");
    }

    @Test
    void nestedSplitKeepsOriginalOfOuterSplit() {
        // only the outer split has a threshold, the inner split fails the outer item
        List<List<String>> body = List.of(List.of("a", "FAIL"), List.of("b"));
        Exchange out = send("direct:nested", body);
        assertOriginalKept(out, body);
        assertNull(out.getMessage().getHeader("outerItem"), "header of the failed outer split item");
    }

    @Test
    void nestedSplitsWithThresholdsKeepOriginalOfOuterSplit() {
        List<List<String>> body = List.of(List.of("a", "FAIL"), List.of("b"));
        Exchange out = send("direct:nestedBoth", body);
        assertOriginalKept(out, body);
        assertNull(out.getMessage().getHeader("outerItem"), "header of the failed outer split item");
    }

    @Test
    void errorThresholdKeepsOriginal() {
        Exchange out = send("direct:ratio", "a,b,c");
        assertOriginalKept(out, "a,b,c");
        assertTrue(out.getProperty(Exchange.SPLIT_RESULT, SplitResult.class).isAborted());
    }

    @Test
    void parallelMaxFailedRecordsKeepsOriginal() {
        assertOriginalKept(send("direct:parallel", "a,b,c"), "a,b,c");
    }

    @Test
    void streamingMaxFailedRecordsKeepsOriginal() {
        assertOriginalKept(send("direct:streaming", "a,b,c"), "a,b,c");
    }

    @Test
    void transactedMaxFailedRecordsKeepsOriginal() {
        // the transacted code path (MulticastTransactedTask)
        assertOriginalKept(send("direct:max", "a,b,c", true), "a,b,c");
    }

    @Test
    void sameExceptionAndPropertiesAsStopOnException() {
        Exchange stop = send("direct:stop", "a,b,c");
        Exchange max = send("direct:max", "a,b,c");

        // the same exception, which says which split item failed (and the id of its exchange)
        assertTrue(stop.getException().getMessage().startsWith("Multicast processing failed for number 0."));
        assertTrue(max.getException().getMessage().startsWith("Multicast processing failed for number 0."));
        assertEquals(stop.getException().getClass(), max.getException().getClass());
        assertEquals(stop.getException().getCause().getClass(), max.getException().getCause().getClass());
        assertEquals(stop.getException().getCause().getMessage(), max.getException().getCause().getMessage());
        assertEquals(stop.getExchangeExtension().isRedeliveryExhausted(), max.getExchangeExtension().isRedeliveryExhausted());
        Set<String> stopProps = new TreeSet<>(stop.getProperties().keySet());
        Set<String> maxProps = new TreeSet<>(max.getProperties().keySet());
        // only the threshold options set the split result
        maxProps.remove(Exchange.SPLIT_RESULT);
        assertEquals(stopProps, maxProps);
    }

    @Test
    void doCatchAroundSplitGetsOriginal() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("a,b,c");
        error.expectedHeaderReceived("orig", "yes");
        error.message(0).header("item").isNull();

        send("direct:trycatch", "a,b,c");

        error.assertIsSatisfied();
    }

    @Test
    void doCatchAroundStopOnExceptionSplitGetsOriginal() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("a,b,c");
        error.expectedHeaderReceived("orig", "yes");
        error.message(0).header("item").isNull();

        send("direct:trycatchstop", "a,b,c");

        error.assertIsSatisfied();
    }

    @Test
    void aggregationStrategySeesTheStopAsWithStopOnException() {
        Exchange stop = send("direct:aggstop", "ok,FAIL,ok");
        Exchange max = send("direct:aggmax", "ok,FAIL,ok");

        assertOriginalKept(stop, "ok,FAIL,ok");
        assertOriginalKept(max, "ok,FAIL,ok");
        // the item before the failed one is aggregated, the failed one is only given to onCompletion
        assertEquals(List.of("ok"), stopStrategy.aggregated);
        assertEquals(stopStrategy.aggregated, maxStrategy.aggregated);
        assertNotNull(stopStrategy.completedWith);
        assertNotNull(maxStrategy.completedWith);
        assertInstanceOf(CamelExchangeException.class, maxStrategy.completedWith.getException());
        assertEquals("FAIL", maxStrategy.completedWith.getMessage().getHeader("item"));
        assertEquals(stopStrategy.completedWith.getMessage().getHeader("item"),
                maxStrategy.completedWith.getMessage().getHeader("item"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:stop").split(body()).stopOnException().to("direct:item").end();
                from("direct:max").split(body()).maxFailedRecords(1).to("direct:item").end();
                from("direct:max2").split(body()).maxFailedRecords(2).to("direct:item").end();
                from("direct:ratio").split(body()).errorThreshold(0.5).to("direct:item").end();
                from("direct:parallel").split(body()).parallelProcessing().maxFailedRecords(1).to("direct:item").end();
                from("direct:streaming").split(body()).streaming().maxFailedRecords(1).to("direct:item").end();
                from("direct:share").split(body()).shareUnitOfWork().maxFailedRecords(1).to("direct:item").end();

                from("direct:nested").split(body()).maxFailedRecords(1)
                        .setHeader("outerItem", simple("${body}"))
                        .split(body()).to("direct:failing").end()
                        .end();
                from("direct:nestedBoth").split(body()).maxFailedRecords(1)
                        .setHeader("outerItem", simple("${body}"))
                        .split(body()).maxFailedRecords(1).to("direct:failing").end()
                        .end();

                from("direct:trycatch")
                        .doTry()
                            .split(body()).maxFailedRecords(1).to("direct:item").end()
                        .endDoTry()
                        .doCatch(Exception.class)
                            .to("mock:error")
                        .end();
                from("direct:trycatchstop")
                        .doTry()
                            .split(body()).stopOnException().to("direct:item").end()
                        .endDoTry()
                        .doCatch(Exception.class)
                            .to("mock:error")
                        .end();

                from("direct:aggstop").split(body(), stopStrategy).stopOnException().to("direct:failing").end();
                from("direct:aggmax").split(body(), maxStrategy).maxFailedRecords(1).to("direct:failing").end();

                from("direct:item")
                        .setHeader("item", simple("${body}"))
                        .setProperty("itemProp", constant("x"))
                        .removeProperty("origProp")
                        .setVariable("itemVar", constant("x"))
                        .setBody(constant("changed"))
                        .throwException(new IllegalStateException("boom"));

                from("direct:failing")
                        .setHeader("item", simple("${body}"))
                        .setProperty("itemProp", constant("x"))
                        .removeProperty("origProp")
                        .setVariable("itemVar", constant("x"))
                        .filter(body().isEqualTo("FAIL"))
                        .throwException(new IllegalStateException("boom"));
            }
        };
    }

    private static final class RecordingAggregationStrategy implements AggregationStrategy {

        private final List<String> aggregated = new ArrayList<>();
        private volatile Exchange completedWith;

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            aggregated.add(newExchange.getMessage().getBody(String.class));
            return newExchange;
        }

        @Override
        public void onCompletion(Exchange exchange, Exchange inputExchange) {
            completedWith = exchange;
        }
    }
}
