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

public class CustomJarsITCase extends JBangTestSupport {

    @Test
    public void testCustomJars() throws IOException {
        copyResourceInDataFolder(TestResources.CIRCUIT_BREAKER);
        Assertions
                .assertThatCode(() -> execute(
                        String.format("run %s/CircuitBreakerRoute.java --max-seconds=30", mountPoint())))
                .as("the application without dependency will cause error")
                .hasStackTraceContaining("Failed to create route: circuitBreaker")
                .hasStackTraceContaining(
                        "Cannot find camel-resilience4j or camel-microprofile-fault-tolerance on the classpath.");
        executeBackground(String.format("run %s/CircuitBreakerRoute.java --dep=camel-timer,camel-resilience4j", mountPoint()));
        checkLogContains("timer called");
        checkLogContains("Fallback message", 10);
    }
}
