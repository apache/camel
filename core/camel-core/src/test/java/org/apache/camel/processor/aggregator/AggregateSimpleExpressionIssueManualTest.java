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
package org.apache.camel.processor.aggregator;

import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Manual unit test")
public class AggregateSimpleExpressionIssueManualTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AggregateSimpleExpressionIssueManualTest.class);
    private static final String DATA = "100,200,1,123456,2010-03-01T12:13:14,100,USD,Best Buy,5045,Santa Monica,CA,Type\n";

    private MyBean myBean = new MyBean();
    private AggStrategy aggStrategy = new AggStrategy();

    @Test
    public void testAggregateSimpleExpression() throws Exception {
        // 10 files + 10 files * 100 batches
        int files = 10;
        int rows = 100000;
        int batches = rows / 1000;
        int total = files + (files * rows) + (files * batches);
        LOG.info("There are {} exchanges", total);
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(total).create();

        LOG.info("Writing 10 files with 100000 rows in each file");
        // write 10 files of 100k rows
        for (int i = 0; i < files; i++) {
            try (Writer out = Files.newBufferedWriter(testFile("data" + i))) {
                for (int j = 0; j < rows; j++) {
                    out.write(DATA);
                }
            }
        }

        // start the route
        StopWatch watch = new StopWatch();
        context.getRouteController().startRoute("foo");

        LOG.info("Waiting to process all the files");
        boolean matches = notify.matches(3, TimeUnit.MINUTES);
        LOG.info("Should process all files {}", matches);

        LOG.info("Time taken {} ms", watch.taken());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri()).routeId("foo").noAutoStartup().log("Picked up ${file:name}").split()
                        .tokenize("\n").streaming()
                        .aggregate(constant(true), aggStrategy).completionSize(simple("1000")).completionTimeout(simple("500"))
                        .bean(myBean).end().end();
            }
        };
    }

    public static final class MyBean {
        private volatile int cnt;

        public void invoke(final List<String> strList) {
            LOG.info("Batch {}", ++cnt);
        }
    }

    public static final class AggStrategy implements AggregationStrategy {

        private final int batchSize = 1000;

        @Override
        @SuppressWarnings("unchecked")
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String str = newExchange.getIn().getBody(String.class);

            if (oldExchange == null) {
                List<String> list = new ArrayList<>(batchSize);
                list.add(str);
                newExchange.getIn().setBody(list);
                return newExchange;
            }

            List<String> list = oldExchange.getIn().getBody(List.class);
            list.add(str);

            return oldExchange;
        }

    }

}
