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
package org.apache.camel.component.hawtdb;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test issue with hawtdb file store growing to large
 */
@Ignore("Run this test manually")
public class HawtDBBigPayloadTest extends CamelTestSupport {

    private static final long TIME = 60 * 1000;
    private static final AtomicLong NUMBER = new AtomicLong();
    private HawtDBAggregationRepository repo;

    @Before
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        repo = new HawtDBAggregationRepository("repo1", "target/data/hawtdb.dat");
        super.setUp();
    }

    @Test
    public void testBigPayload() throws Exception {

        log.info("Running test for " + TIME + " millis.");
        Thread.sleep(60 * 1000);

        // assert the file size of the repo is not big < 32mb
        File file = new File("target/data/hawtdb.dat");
        assertTrue(file + " should exists", file.exists());
        long size = file.length();
        log.info(file + " size is " + size);
        // should be about 32mb, so we say 34 just in case
        assertTrue(file + " should not be so big in size, was: " + size, size < 34 * 1024 * 1024);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo")
                    .bean(BigPayload.class)
                    .aggregate(method(HawtDBBigPayloadTest.class, "number"), new UseLatestAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(2).completionTimeout(5000)
                        .log("Aggregated key ${header.CamelAggregatedCorrelationKey}");
            }
        };
    }

    public static long number() {
        // return 123; (will not cause hawtdb to grow in size)
        return NUMBER.incrementAndGet();
    }

}
