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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SplitterGroupTest extends ContextTestSupport {

    @Test
    void testSplitterGroup() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(3);

        template.sendBody("direct:start", Arrays.asList("a", "b", "c", "d", "e", "f", "g"));

        mock.assertIsSatisfied();

        // First group: [a, b, c]
        Object body0 = mock.getReceivedExchanges().get(0).getIn().getBody();
        assertInstanceOf(List.class, body0);
        assertEquals(3, ((List<?>) body0).size());

        // Second group: [d, e, f]
        Object body1 = mock.getReceivedExchanges().get(1).getIn().getBody();
        assertInstanceOf(List.class, body1);
        assertEquals(3, ((List<?>) body1).size());

        // Third group: [g] (remainder)
        Object body2 = mock.getReceivedExchanges().get(2).getIn().getBody();
        assertInstanceOf(List.class, body2);
        assertEquals(1, ((List<?>) body2).size());
    }

    @Test
    void testSplitterGroupExactMultiple() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", Arrays.asList("a", "b", "c", "d", "e", "f"));

        mock.assertIsSatisfied();

        assertEquals(3, ((List<?>) mock.getReceivedExchanges().get(0).getIn().getBody()).size());
        assertEquals(3, ((List<?>) mock.getReceivedExchanges().get(1).getIn().getBody()).size());
    }

    @Test
    void testSplitterGroupSingleItem() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", List.of("only"));

        mock.assertIsSatisfied();

        List<?> body = (List<?>) mock.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(1, body.size());
        assertEquals("only", body.get(0));
    }

    @Test
    void testSplitterGroupWithParallelProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-split");
        mock.expectedMessageCount(3);

        template.sendBody("direct:parallel", Arrays.asList(1, 2, 3, 4, 5, 6, 7));

        mock.assertIsSatisfied();
    }

    @Test
    void testGroupWithMaxFailedRecords() throws Exception {
        // 9 items grouped by 3. Processor throws if the chunk contains "FAIL".
        // Items: a, b, FAIL, d, e, f, g, h, i → chunks [a,b,FAIL], [d,e,f], [g,h,i]
        // First chunk triggers failure, maxFailedRecords=2, so processing continues
        MockEndpoint mock = getMockEndpoint("mock:group-fail");
        mock.expectedMinimumMessageCount(2); // at least the 2 non-failing chunks

        Exchange result = template.send("direct:group-fail",
                e -> e.getIn().setBody(Arrays.asList("a", "b", "FAIL", "d", "e", "f", "g", "h", "i")));

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertEquals(1, splitResult.getFailureCount());
        assertFalse(splitResult.isAborted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).group(3)
                        .to("mock:split");

                from("direct:parallel")
                        .split(body()).group(3).parallelProcessing()
                        .to("mock:parallel-split");

                from("direct:group-fail")
                        .split(body()).group(3).maxFailedRecords(2)
                        .process(exchange -> {
                            List<?> chunk = exchange.getIn().getBody(List.class);
                            for (Object item : chunk) {
                                if ("FAIL".equals(item)) {
                                    throw new IllegalArgumentException("Chunk contains FAIL");
                                }
                            }
                        })
                        .to("mock:group-fail");
            }
        };
    }
}
