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

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test issue with leveldb file store growing to large
 */
@Disabled("Run this test manually")
@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBBigPayloadManualTest extends LevelDBTestSupport {

    private static final long TIME = 60 * 1000;
    private static final AtomicLong NUMBER = new AtomicLong();
    private Logger log = LoggerFactory.getLogger(getClass());

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testBigPayload() throws Exception {

        log.info("Running test for {} millis.", TIME);
        Thread.sleep(60 * 1000);

        // assert the file size of the repo is not big < 32mb
        File file = new File("target/data/leveldb.dat");
        assertTrue(file.exists(), file + " should exists");
        long size = file.length();
        log.info("{} size is {}", file, size);
        // should be about 32mb, so we say 34 just in case
        assertTrue(size < 34 * 1024 * 1024, file + " should not be so big in size, was: " + size);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:foo")
                        .bean(BigPayload.class)
                        .aggregate(method(LevelDBBigPayloadManualTest.class, "number"), new UseLatestAggregationStrategy())
                        .aggregationRepository(getRepo())
                        .completionSize(2).completionTimeout(5000)
                        .log("Aggregated key ${header.CamelAggregatedCorrelationKey}");
            }
        };
    }

    public static long number() {
        // return 123; (will not cause leveldb to grow in size)
        return NUMBER.incrementAndGet();
    }

}
