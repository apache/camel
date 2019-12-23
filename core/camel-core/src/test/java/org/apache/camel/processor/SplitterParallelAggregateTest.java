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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class SplitterParallelAggregateTest extends ContextTestSupport {

    // run this test manually as it takes some time to process, but shows that
    // parallel aggregate can
    // be faster when enabled.
    private boolean enabled;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:splitSynchronizedAggregation").split(method(new MySplitter(), "rowIterator"), new MyAggregationStrategy()).to("log:someSplitProcessing?groupSize=500");

                from("direct:splitUnsynchronizedAggregation").split(method(new MySplitter(), "rowIterator"), new MyAggregationStrategy()).parallelAggregate()
                    .to("log:someSplitProcessing?groupSize=500");
            }
        };
    }

    @Test
    public void test1() throws Exception {
        if (!enabled) {
            return;
        }
        int numberOfRequests = 1;
        timeSplitRoutes(numberOfRequests);
    }

    @Test
    public void test2() throws Exception {
        if (!enabled) {
            return;
        }
        int numberOfRequests = 2;
        timeSplitRoutes(numberOfRequests);
    }

    @Test
    public void test4() throws Exception {
        if (!enabled) {
            return;
        }
        int numberOfRequests = 4;
        timeSplitRoutes(numberOfRequests);
    }

    protected void timeSplitRoutes(int numberOfRequests) throws Exception {
        String[] endpoints = new String[] {"direct:splitSynchronizedAggregation", "direct:splitUnsynchronizedAggregation"};
        List<Future<File>> futures = new ArrayList<>();
        StopWatch stopWatch = new StopWatch(false);

        for (String endpoint : endpoints) {
            stopWatch.restart();
            for (int requestIndex = 0; requestIndex < numberOfRequests; requestIndex++) {
                futures.add(template.asyncRequestBody(endpoint, null, File.class));
            }

            for (int i = 0; i < futures.size(); i++) {
                Future<File> future = futures.get(i);
                future.get();
            }
            stopWatch.taken();

            log.info(String.format("test%d.%s=%d\n", numberOfRequests, endpoint, stopWatch.taken()));
        }
    }

    public static class MySplitter {
        public Iterator<String[]> rowIterator() {
            // we would normally be reading a large file but for this test,
            // we'll just manufacture a bunch of string
            // arrays
            LinkedList<String[]> rows = new LinkedList<>();
            String[] row;
            for (int i = 0; i < 10000; i++) {
                row = new String[10];
                for (int j = 0; j < row.length; j++) {
                    row[j] = String.valueOf(System.nanoTime());
                }
                rows.add(row);
            }

            return rows.iterator();
        }
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {

            // emulate some processing
            Random random = new Random(System.currentTimeMillis());
            for (int i = 0; i < 10000; i++) {
                random.nextGaussian();
            }

            return newExchange;
        }
    }

}
