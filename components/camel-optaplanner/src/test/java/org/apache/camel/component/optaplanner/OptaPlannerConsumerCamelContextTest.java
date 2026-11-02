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
package org.apache.camel.component.optaplanner;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A consumer gets the best solutions of the producers of its own CamelContext only.
 */
public class OptaPlannerConsumerCamelContextTest extends CamelTestSupport {

    private static final String CONFIG_FILE = "org/apache/camel/component/optaplanner/solverConfigQuick.xml";

    private CamelContext otherContext;

    @AfterEach
    public void stopOtherContext() {
        if (otherContext != null) {
            otherContext.stop();
        }
    }

    @Test
    public void testConsumerOfOtherCamelContextGetsNoEvents() throws Exception {
        // a consumer in another CamelContext, with the same (default) problemId
        otherContext = new DefaultCamelContext();
        otherContext.addRoutes(new RouteBuilder() {
            public void configure() {
                from("optaplanner:myName?configFile=" + CONFIG_FILE)
                        .to("mock:other");
            }
        });
        otherContext.start();
        MockEndpoint other = otherContext.getEndpoint("mock:other", MockEndpoint.class);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        CloudBalance problem = new CloudBalancingGenerator(true).createCloudBalance(4, 12);
        CloudBalance bestSolution = template.requestBody("direct:in", problem, CloudBalance.class);
        assertNotNull(bestSolution.getScore());

        mock.assertIsSatisfied();
        await().during(500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> other.getReceivedCounter() == 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("optaplanner:myName?async=true&configFile=" + CONFIG_FILE);

                from("optaplanner:myName?configFile=" + CONFIG_FILE)
                        .to("mock:result");
            }
        };
    }

}
