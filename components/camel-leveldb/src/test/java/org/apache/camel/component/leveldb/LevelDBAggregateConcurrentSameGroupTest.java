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
package org.apache.camel.component.leveldb;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateConcurrentSameGroupTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    @Test
    public void testMoreConcurrentProducers() throws Exception {
        doSendMessages(50, 10);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.setResultWaitTime(30 * 1000L);
        mock.expectedMessageCount(1);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader("direct:start", index, "id", 123);
                    // simulate a little delay
                    Thread.sleep(3);
                    return null;
                }
            });
        }

        MockEndpoint.assertIsSatisfied(context);
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                LevelDBAggregationRepository repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");

                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                        .completionTimeout(1000).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
        };
    }
}
