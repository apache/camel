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
import java.util.regex.Pattern;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.examples.cloudbalancing.domain.CloudBalance;
import org.optaplanner.examples.cloudbalancing.persistence.CloudBalancingGenerator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * With a configFile, the endpoint creates one SolverManager, which it closes when it stops.
 */
public class OptaPlannerConfigFileSolverManagerTest extends CamelTestSupport {

    // the SolverManager runs its solvers on a JDK fixed thread pool, whose threads are named pool-N-thread-M
    private static final Pattern POOL_THREAD = Pattern.compile("pool-\\d+-thread-\\d+");

    @Test
    public void testOneSolverManagerPerEndpoint() throws Exception {
        int parallelSolverCount = new SolverManagerConfig().resolveParallelSolverCount();
        long before = countPoolThreads();

        // more exchanges than one SolverManager has solver threads
        for (int i = 0; i < parallelSolverCount + 2; i++) {
            CloudBalance problem = new CloudBalancingGenerator(true).createCloudBalance(4, 12);
            CloudBalance bestSolution = template.requestBody("direct:in", problem, CloudBalance.class);
            assertNotNull(bestSolution.getScore());
        }

        // a SolverManager per exchange would keep a solver thread alive per exchange
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> countPoolThreads() - before <= parallelSolverCount);

        // the endpoint closes its SolverManager when it stops
        context.stop();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> countPoolThreads() <= before);
    }

    private static long countPoolThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> POOL_THREAD.matcher(t.getName()).matches())
                .count();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("optaplanner:myName?configFile=org/apache/camel/component/optaplanner/solverConfigQuick.xml");
            }
        };
    }

}
