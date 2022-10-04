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

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs({ OS.AIX, OS.OTHER })
@Parameterized
public class LevelDBBinaryTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        byte[] a = new byte[10];
        new SecureRandom().nextBytes(a);
        byte[] b = new byte[10];
        new SecureRandom().nextBytes(b);
        byte[] c = new byte[10];
        new SecureRandom().nextBytes(c);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(a);
            outputStream.write(b);
            outputStream.write(c);

            mock.expectedBodiesReceived(outputStream.toByteArray());
        }

        template.sendBodyAndHeader("direct:start", a, "id", 123);
        template.sendBodyAndHeader("direct:start", b, "id", 123);
        template.sendBodyAndHeader("direct:start", c, "id", 123);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        // from endpoint should be preserved
        assertEquals("direct://start", mock.getReceivedExchanges().get(0).getFromEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() {
                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new ByteAggregationStrategy())
                        // use our created leveldb repo as aggregation repository
                        .completionSize(3).aggregationRepository(getRepo())
                        .to("mock:aggregated");
            }
            // END SNIPPET: e1
        };
    }
}
