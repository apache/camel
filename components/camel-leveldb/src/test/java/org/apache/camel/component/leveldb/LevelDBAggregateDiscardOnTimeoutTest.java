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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateDiscardOnTimeoutTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testAggregateDiscardOnTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        // wait at most 3 seconds
        Awaitility.await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> mock.assertIsSatisfied());

        mock.assertIsSatisfied();

        // now send 3 which does not timeout
        mock.reset();
        mock.expectedBodiesReceived("C+D+E");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        // should complete before timeout
        mock.await(1500, TimeUnit.MILLISECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                        .completionSize(3).aggregationRepository(getRepo())
                        // use a 3 second timeout
                        .completionTimeout(2000)
                        // and if timeout occurred then just discard the aggregated message
                        .discardOnCompletionTimeout()
                        .to("mock:aggregated");
            }
        };
    }

    @Override
    LevelDBAggregationRepository getRepo() {
        LevelDBAggregationRepository repo = super.getRepo();
        repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");
        // enable recovery
        repo.setUseRecovery(true);
        // check faster
        repo.setRecoveryInterval(500, TimeUnit.MILLISECONDS);

        return repo;
    }
}
