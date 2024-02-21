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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.util.HeaderDto;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateSerializedHeadersTest extends LevelDBTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBAggregateSerializedHeadersTest.class);
    private static final int SIZE = 500;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        getRepo().setAllowSerializedHeaders(true);
        super.setUp();
    }

    @Test
    public void testLoadTestLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(50 * 1000);

        LOG.info("Starting to send {} messages.", SIZE);

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            HeaderDto headerDto = new HeaderDto("test", "company", 1);
            char id = 'A';
            LOG.debug("Sending {} with id {}", value, id);
            Map<String, Object> headers = new HashMap<>();
            headers.put("id", headerDto);
            template.sendBodyAndHeaders("seda:start?size=" + SIZE, value, headers);
        }

        LOG.info("Sending all {} message done. Now waiting for aggregation to complete.", SIZE);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start?size=" + SIZE)
                        .to("log:input?groupSize=500")
                        .aggregate(header("id"), new IntegerAggregationStrategy())
                            .aggregationRepository(getRepo())
                            .completionSize(SIZE)
                            .to("log:output?showHeaders=true")
                            .to("mock:result")
                        .end();
            }
        };
    }
}
