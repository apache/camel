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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateTimeoutCompletionRestartTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testLevelDBAggregateTimeoutCompletionRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        // stop Camel
        context.stop();
        assertEquals(0, mock.getReceivedCounter());

        // start Camel again, and the timeout should trigger a completion
        context.start();

        mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("ABC");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, mock.getReceivedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                        // use our created leveldb repo as aggregation repository
                        .completionTimeout(3000).aggregationRepository(getRepo())
                        .to("mock:aggregated");
            }
        };
    }
}
