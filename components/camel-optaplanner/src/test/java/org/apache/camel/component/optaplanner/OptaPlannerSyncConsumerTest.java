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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A consumer also gets the best solutions of a producer that solves synchronously.
 */
public class OptaPlannerSyncConsumerTest extends CamelTestSupport {

    private static final String CONFIG_FILE = "org/apache/camel/component/optaplanner/solverConfigQuick.xml";

    @Test
    public void testConsumerGetsEventsOfSyncProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        CloudBalance problem = new CloudBalancingGenerator(true).createCloudBalance(4, 12);
        CloudBalance bestSolution = template.requestBody("direct:in", problem, CloudBalance.class);
        assertNotNull(bestSolution.getScore());

        mock.assertIsSatisfied();
        CloudBalance event = mock.getReceivedExchanges().get(0).getMessage()
                .getHeader(OptaPlannerConstants.BEST_SOLUTION, CloudBalance.class);
        assertNotNull(event);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("optaplanner:myName?configFile=" + CONFIG_FILE);

                from("optaplanner:myName?configFile=" + CONFIG_FILE)
                        .to("mock:result");
            }
        };
    }

}
