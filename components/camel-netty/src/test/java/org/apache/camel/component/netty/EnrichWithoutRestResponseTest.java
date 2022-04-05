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
package org.apache.camel.component.netty;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Regression test for CAMEL-16178
 */
class EnrichWithoutRestResponseTest extends BaseNettyTest {
    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                // mock server - accepts connection and immediately disconnects without any response
                from("netty:tcp://0.0.0.0:{{port}}?disconnect=true")
                        .log("Got request ${body}")
                        .setBody(constant(null));

                // test routes
                final String nettyClientUri
                        = "netty:tcp://127.0.0.1:{{port}}?textline=true&connectTimeout=1000&requestTimeout=1000";
                from("direct:reqTo")
                        .to(nettyClientUri);
                from("direct:reqEnrich")
                        .enrich(nettyClientUri);
                from("direct:reqEnrichShareUoW")
                        .enrich(nettyClientUri, new UseLatestAggregationStrategy(), true, true);
            }
        };
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void toTest() {
        assertThatExceptionOfType(CamelExecutionException.class)
                .isThrownBy(() -> template.requestBody("direct:reqTo", ""))
                .havingCause().withMessageContaining("No response received from remote server");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void enrichTest() {
        assertThatExceptionOfType(CamelExecutionException.class)
                .isThrownBy(() -> template.requestBody("direct:reqEnrich", ""))
                .havingCause().withMessageContaining("No response received from remote server");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void enrichShareUoWTest() {
        assertThatExceptionOfType(CamelExecutionException.class)
                .isThrownBy(() -> template.requestBody("direct:reqEnrichShareUoW", ""))
                .havingCause().withMessageContaining("No response received from remote server");
    }
}
