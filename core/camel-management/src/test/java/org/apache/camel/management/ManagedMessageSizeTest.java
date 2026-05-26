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
package org.apache.camel.management;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedMessageSizeTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageSize(true);
        context.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(true);
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testMessageSizeOnEndpoints() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        String body1 = "Hello World";
        template.sendBodyAndHeader("seda:start", body1, "myHeader", "myValue");

        String body2 = "Bye World";
        template.sendBodyAndHeader("seda:start", body2, "myHeader", "myValue");

        assertMockEndpointsSatisfied();

        long body1Size = body1.getBytes(StandardCharsets.UTF_8).length;
        long body2Size = body2.getBytes(StandardCharsets.UTF_8).length;

        List<RuntimeEndpointRegistry.Statistic> stats = context.getRuntimeEndpointRegistry().getEndpointStatistics();

        // find the input endpoint (seda:start, direction "in")
        RuntimeEndpointRegistry.Statistic inputStat = stats.stream()
                .filter(s -> "in".equals(s.getDirection()) && s.getUri().contains("seda://start"))
                .findFirst()
                .orElse(null);

        assertNotNull(inputStat, "Should find input endpoint statistic");
        assertEquals(2, inputStat.getHits());
        assertEquals(Math.min(body1Size, body2Size), inputStat.getMinBodySize());
        assertEquals(Math.max(body1Size, body2Size), inputStat.getMaxBodySize());
        assertEquals((body1Size + body2Size) / 2, inputStat.getMeanBodySize());
        assertTrue(inputStat.getMinHeadersSize() > 0, "MinHeadersSize should be positive");
        assertTrue(inputStat.getMaxHeadersSize() > 0, "MaxHeadersSize should be positive");
        assertTrue(inputStat.getMeanHeadersSize() > 0, "MeanHeadersSize should be positive");

        // find the output endpoint (mock:result, direction "out")
        RuntimeEndpointRegistry.Statistic outputStat = stats.stream()
                .filter(s -> "out".equals(s.getDirection()) && s.getUri().contains("mock://result"))
                .findFirst()
                .orElse(null);

        assertNotNull(outputStat, "Should find output endpoint statistic");
        assertEquals(2, outputStat.getHits());
        assertEquals(Math.min(body1Size, body2Size), outputStat.getMinBodySize());
        assertEquals(Math.max(body1Size, body2Size), outputStat.getMaxBodySize());
    }

    @Test
    public void testExchangePropertiesSet() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String body = "Hello World";
        template.sendBody("seda:start", body);

        assertMockEndpointsSatisfied();

        long expectedBodySize = body.getBytes(StandardCharsets.UTF_8).length;
        Long actualBodySize = getMockEndpoint("mock:result").getExchanges().get(0)
                .getProperty("CamelMessageBodySize", Long.class);
        assertEquals(expectedBodySize, actualBodySize);

        Long actualHeadersSize = getMockEndpoint("mock:result").getExchanges().get(0)
                .getProperty("CamelMessageHeadersSize", Long.class);
        assertNotNull(actualHeadersSize, "Headers size should be set");
        assertTrue(actualHeadersSize >= 0, "Headers size should be non-negative");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("route1")
                        .to("mock:result");
            }
        };
    }
}
