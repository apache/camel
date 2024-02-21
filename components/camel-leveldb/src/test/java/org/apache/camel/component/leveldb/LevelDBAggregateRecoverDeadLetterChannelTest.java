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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateRecoverDeadLetterChannelTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        // enable recovery
        getRepo().setUseRecovery(true);
        // exhaust after at most 3 attempts
        getRepo().setMaximumRedeliveries(3);
        // and move to this dead letter channel
        getRepo().setDeadLetterUri("mock:dead");
        // check faster
        getRepo().setRecoveryInterval(500, TimeUnit.MILLISECONDS);

        super.setUp();
    }

    @Test
    public void testLevelDBAggregateRecoverDeadLetterChannel() throws Exception {
        // should fail all times
        getMockEndpoint("mock:result").expectedMessageCount(0);

        getMockEndpoint("mock:aggregated").expectedMessageCount(4);
        getMockEndpoint("mock:aggregated").message(0).header(Exchange.REDELIVERED).isNull();
        getMockEndpoint("mock:aggregated").message(1).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        getMockEndpoint("mock:aggregated").message(1).header(Exchange.REDELIVERY_COUNTER).isEqualTo(1);
        getMockEndpoint("mock:aggregated").message(1).header(Exchange.REDELIVERY_MAX_COUNTER).isEqualTo(3);
        getMockEndpoint("mock:aggregated").message(2).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        getMockEndpoint("mock:aggregated").message(2).header(Exchange.REDELIVERY_COUNTER).isEqualTo(2);
        getMockEndpoint("mock:aggregated").message(2).header(Exchange.REDELIVERY_MAX_COUNTER).isEqualTo(3);
        getMockEndpoint("mock:aggregated").message(3).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        getMockEndpoint("mock:aggregated").message(3).header(Exchange.REDELIVERY_COUNTER).isEqualTo(3);
        getMockEndpoint("mock:aggregated").message(3).header(Exchange.REDELIVERY_MAX_COUNTER).isEqualTo(3);

        getMockEndpoint("mock:dead").expectedBodiesReceived("ABCDE");
        getMockEndpoint("mock:dead").message(0).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        getMockEndpoint("mock:dead").message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(3);
        getMockEndpoint("mock:dead").message(0).header(Exchange.REDELIVERY_MAX_COUNTER).isNull();

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                        .completionSize(5).aggregationRepository(getRepo())
                        .log("aggregated exchange id ${exchangeId} with ${body}")
                        .to("mock:aggregated")
                        .throwException(new IllegalArgumentException("Damn"))
                        .to("mock:result")
                        .end();
            }
        };
    }
}
