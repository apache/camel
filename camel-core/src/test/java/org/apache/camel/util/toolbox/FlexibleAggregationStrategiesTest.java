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
package org.apache.camel.util.toolbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Node;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.toolbox.FlexibleAggregationStrategy.CompletionAwareMixin;
import org.apache.camel.util.toolbox.FlexibleAggregationStrategy.TimeoutAwareMixin;
import org.junit.Test;

/**
 * Unit tests for the {@link FlexibleAggregationStrategy}.
 * @since 2.12
 */
public class FlexibleAggregationStrategiesTest extends ContextTestSupport {
    
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final CountDownLatch timeoutLatch = new CountDownLatch(1);
    
    @Test @SuppressWarnings("unchecked")
    public void testFlexibleAggregationStrategyNoCondition() throws Exception {
        getMockEndpoint("mock:result1").expectedMessageCount(1);
        getMockEndpoint("mock:result1").message(0).body().isInstanceOf(ArrayList.class);
        
        template.sendBodyAndHeader("direct:start1", "AGGREGATE1", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE2", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE3", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE4", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE5", "id", "123");

        assertMockEndpointsSatisfied();
        
        List<String> resultList = getMockEndpoint("mock:result1").getReceivedExchanges()
                .get(0).getIn().getBody(List.class);
        
        for (int i = 0; i < 5; i++) {
            assertEquals("AGGREGATE" + (i + 1), resultList.get(i));
        }
    }
    
    @Test @SuppressWarnings("unchecked")
    public void testFlexibleAggregationStrategyCondition() throws Exception {
        getMockEndpoint("mock:result1").expectedMessageCount(1);
        getMockEndpoint("mock:result1").message(0).body().isInstanceOf(ArrayList.class);
        
        template.sendBodyAndHeader("direct:start1", "AGGREGATE1", "id", "123");
        template.sendBodyAndHeader("direct:start1", "DISCARD", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE2", "id", "123");
        template.sendBodyAndHeader("direct:start1", "DISCARD", "id", "123");
        template.sendBodyAndHeader("direct:start1", "AGGREGATE3", "id", "123");

        assertMockEndpointsSatisfied();
        
        List<String> resultList = getMockEndpoint("mock:result1").getReceivedExchanges()
                .get(0).getIn().getBody(List.class);
        for (int i = 0; i < 3; i++) {
            assertEquals("AGGREGATE" + (i + 1), resultList.get(i));
        }
    }
    
    @Test @SuppressWarnings("unchecked")
    public void testFlexibleAggregationStrategyStoreInPropertyHashSet() throws Exception {
        getMockEndpoint("mock:result2").expectedMessageCount(1);
        getMockEndpoint("mock:result2").message(0).exchangeProperty("AggregationResult").isInstanceOf(HashSet.class);
        
        template.sendBodyAndHeader("direct:start2", "ignored body", "input", "AGGREGATE1");
        template.sendBodyAndHeader("direct:start2", "ignored body", "input", "DISCARD");
        template.sendBodyAndHeader("direct:start2", "ignored body", "input", "AGGREGATE2");
        template.sendBodyAndHeader("direct:start2", "ignored body", "input", "DISCARD");
        template.sendBodyAndHeader("direct:start2", "ignored body", "input", "AGGREGATE3");

        assertMockEndpointsSatisfied();
        
        HashSet<String> resultSet = getMockEndpoint("mock:result2").getReceivedExchanges().get(0)
                .getProperty("AggregationResult", HashSet.class);
        assertEquals(3, resultSet.size());
        assertTrue(resultSet.contains("AGGREGATE1") && resultSet.contains("AGGREGATE2") && resultSet.contains("AGGREGATE3"));
    }
    
    @Test
    public void testFlexibleAggregationStrategyStoreInHeaderSingleValue() throws Exception {
        getMockEndpoint("mock:result3").expectedMessageCount(1);
        getMockEndpoint("mock:result3").message(0).header("AggregationResult").isInstanceOf(String.class);
        getMockEndpoint("mock:result3").message(0).header("AggregationResult").isEqualTo("AGGREGATE3");
        
        template.sendBody("direct:start3", "AGGREGATE1");
        template.sendBody("direct:start3", "AGGREGATE2");
        template.sendBody("direct:start3", "AGGREGATE3");

        assertMockEndpointsSatisfied();
    }
    
    @Test @SuppressWarnings("rawtypes")
    public void testFlexibleAggregationStrategyGenericArrayListWithoutNulls() throws Exception {
        getMockEndpoint("mock:result4").expectedMessageCount(1);
        getMockEndpoint("mock:result4").message(0).body().isInstanceOf(ArrayList.class);
        
        template.sendBody("direct:start4", "AGGREGATE1");
        template.sendBody("direct:start4", 123d);
        template.sendBody("direct:start4", null);

        assertMockEndpointsSatisfied();

        ArrayList list = getMockEndpoint("mock:result4").getReceivedExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(2, list.size());
        assertTrue(list.contains("AGGREGATE1"));
        assertTrue(list.contains(123d));
    }
    
    @Test
    public void testFlexibleAggregationStrategyFailWithInvalidCast() throws Exception {
        getMockEndpoint("mock:result5").expectedMessageCount(0);

        try {
            template.sendBody("direct:start5", "AGGREGATE1");
        } catch (Exception exception) {
            assertMockEndpointsSatisfied();
            return;
        }
        
        fail("Type Conversion exception expected, as we are not ignoring invalid casts");
        
    }
    
    @Test @SuppressWarnings("rawtypes")
    public void testFlexibleAggregationStrategyFailOnInvalidCast() throws Exception {
        getMockEndpoint("mock:result6").expectedMessageCount(1);
        getMockEndpoint("mock:result6").message(0).body().isInstanceOf(ArrayList.class);

        template.sendBody("direct:start6", "AGGREGATE1");
        template.sendBody("direct:start6", "AGGREGATE2");
        template.sendBody("direct:start6", "AGGREGATE3");

        ArrayList list = getMockEndpoint("mock:result6").getReceivedExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(3, list.size());
        for (Object object : list) {
            assertNull(object);
        }
        
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testFlexibleAggregationStrategyTimeoutCompletionMixins() throws Exception {
        getMockEndpoint("mock:result.timeoutAndCompletionAware").expectedMessageCount(2);
        getMockEndpoint("mock:result.timeoutAndCompletionAware").message(0).body().isEqualTo("AGGREGATE1");
        getMockEndpoint("mock:result.timeoutAndCompletionAware").message(0).exchangeProperty("Timeout").isEqualTo(true);
        getMockEndpoint("mock:result.timeoutAndCompletionAware").message(1).body().isEqualTo("AGGREGATE3");

        template.sendBody("direct:start.timeoutAndCompletionAware", "AGGREGATE1");
        
        assertTrue(timeoutLatch.await(2500, TimeUnit.MILLISECONDS));
        
        template.sendBody("direct:start.timeoutAndCompletionAware", "AGGREGATE2");
        template.sendBody("direct:start.timeoutAndCompletionAware", "AGGREGATE3");

        assertTrue(completionLatch.await(2500, TimeUnit.MILLISECONDS));

        getMockEndpoint("mock:result.timeoutAndCompletionAware").getReceivedExchanges();
        assertMockEndpointsSatisfied();
    }
    
    @Test @SuppressWarnings("unchecked")
    public void testFlexibleAggregationStrategyPickXPath() throws Exception {
        getMockEndpoint("mock:result.xpath1").expectedMessageCount(1);
        getMockEndpoint("mock:result.xpath1").message(0).body().isInstanceOf(ArrayList.class);

        template.sendBody("direct:start.xpath1", "<envelope><result>ok</result></envelope>");
        template.sendBody("direct:start.xpath1", "<envelope><result>error</result></envelope>");
        template.sendBody("direct:start.xpath1", "<envelope>no result</envelope>");

        assertMockEndpointsSatisfied();
        
        ArrayList<Node> list = getMockEndpoint("mock:result.xpath1").getReceivedExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(2, list.size());
        assertEquals("ok", list.get(0).getTextContent());
        assertEquals("error", list.get(1).getTextContent());
    }

    @Test
    public void testLinkedList() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).and().whenExactlyFailed(0).create();

        template.sendBody("direct:linkedlist", Arrays.asList("FIRST", "SECOND"));

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
    }

    @Test
    public void testHashSet() throws Exception {
        HashSet<String> r = new HashSet<>();
        r.add("FIRST");
        r.add("SECOND");

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).and().whenExactlyFailed(0).create();

        Set result = template.requestBody("direct:hashset", Arrays.asList("FIRST", "SECOND"), Set.class);

        assertTrue(notify.matches(10, TimeUnit.SECONDS));
        assertEquals(r, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                                
                from("direct:start1")
                    .aggregate(AggregationStrategies.flexible(String.class)
                            .accumulateInCollection(ArrayList.class)
                            .pick(simple("${body}"))
                            .condition(simple("${body} contains 'AGGREGATE'")))
                        .header("id").completionSize(5)
                    .to("mock:result1");
                
                from("direct:start2")
                    .aggregate(AggregationStrategies.flexible(String.class)
                            .accumulateInCollection(HashSet.class)
                            .pick(simple("${header.input}"))
                            .condition(simple("${header.input} contains 'AGGREGATE'"))
                            .storeInProperty("AggregationResult"))
                        .constant(true).completionSize(5)
                    .to("mock:result2");
                
                from("direct:start3")
                    .aggregate(AggregationStrategies.flexible(String.class)
                            .storeInHeader("AggregationResult"))
                        .constant(true).completionSize(3)
                    .to("mock:result3");
                
                from("direct:start4")
                    .aggregate(AggregationStrategies.flexible().accumulateInCollection(ArrayList.class))
                        .constant(true).completionSize(3)
                    .to("mock:result4");
                
                from("direct:start5")
                    .aggregate(AggregationStrategies.flexible(Integer.class).accumulateInCollection(ArrayList.class))
                        .constant(true).completionSize(3)
                    .to("mock:result5");

                from("direct:start6")
                    .aggregate(AggregationStrategies.flexible(Integer.class).ignoreInvalidCasts().storeNulls()
                            .accumulateInCollection(ArrayList.class))
                        .constant(true).completionSize(3)
                    .to("mock:result6");
                
                AggregationStrategy timeoutCompletionStrategy = 
                    AggregationStrategies.flexible(String.class)
                        .condition(simple("${body} contains 'AGGREGATE'"))
                        .timeoutAware(new TimeoutAwareMixin() {
                            @Override
                            public void timeout(Exchange exchange, int index, int total, long timeout) {
                                exchange.setProperty("Timeout", true);
                                timeoutLatch.countDown(); 
                            }
                        })
                        .completionAware(new CompletionAwareMixin() {
                            @Override
                            public void onCompletion(Exchange exchange) {
                                completionLatch.countDown();
                            }
                        });
                
                from("direct:start.timeoutAndCompletionAware")
                    .aggregate(timeoutCompletionStrategy).constant(true)
                    .completionTimeout(500).completionSize(2)
                    .to("mock:result.timeoutAndCompletionAware");
                
                from("direct:start.xpath1")
                    .aggregate(AggregationStrategies.flexible(Node.class)
                            .pick(xpath("//result[1]").nodeResult())
                            .accumulateInCollection(ArrayList.class))
                        .constant(true).completionSize(3)
                    .to("mock:result.xpath1");

                from("direct:linkedlist")
                    .log(LoggingLevel.INFO, "Before the first split the body is ${body} and has class ${body.getClass()}")
                        .split(body(), AggregationStrategies.flexible().pick(body()).accumulateInCollection(LinkedList.class))
                    .log(LoggingLevel.INFO, "During the first split the body is ${body} and has class ${body.getClass()}")
                    .end()
                    .log(LoggingLevel.INFO, "Before the second split the body is ${body} and has class ${body.getClass()}")
                        .split(body(), AggregationStrategies.flexible().pick(body()).accumulateInCollection(LinkedList.class))
                    .log(LoggingLevel.INFO, "During the second split the body is ${body} and has class ${body.getClass()}")
                    .end()
                    .log(LoggingLevel.INFO, "After the second split the body is ${body} and has class ${body.getClass()}");

                from("direct:hashset")
                    .log(LoggingLevel.INFO, "Before the first split the body is ${body} and has class ${body.getClass()}")
                        .split(body(), AggregationStrategies.flexible().pick(body()).accumulateInCollection(HashSet.class))
                    .log(LoggingLevel.INFO, "During the first split the body is ${body} and has class ${body.getClass()}")
                    .end()
                    .log(LoggingLevel.INFO, "Before the second split the body is ${body} and has class ${body.getClass()}")
                        .split(body(), AggregationStrategies.flexible().pick(body()).accumulateInCollection(HashSet.class))
                    .log(LoggingLevel.INFO, "During the second split the body is ${body} and has class ${body.getClass()}")
                    .end()
                    .log(LoggingLevel.INFO, "After the second split the body is ${body} and has class ${body.getClass()}");
            }
        };
    }
}