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

package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MetricsObservabilityITCase extends JBangTestSupport {

    @Test
    public void metricsTest() throws IOException {
        copyResourceInDataFolder(TestResources.SERVER_ROUTE);
        executeBackground(String.format("run %s/server.yaml --metrics", mountPoint()));
        checkLogContains("http://0.0.0.0:8080/observe/metrics");
        Assertions.assertThat(execInHost(String.format(
                        "curl http://localhost:%s/observe/metrics", containerService.getDevConsolePort())))
                .as("server should list metrics")
                .contains("# HELP camel_exchanges_total Total number of processed exchanges");
    }

    @Test
    public void circuitBreakerTest() throws IOException {
        copyResourceInDataFolder(TestResources.CIRCUIT_BREAKER);
        executeBackground(String.format("run %s/CircuitBreakerRoute.java --dep=camel-resilience4j", mountPoint()));
        checkLogContains("(CircuitBreakerRoute) started");
        checkCommandOutputs("get circuit-breaker", "resilience4j");
    }
}
