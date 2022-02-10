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
package org.apache.camel.health;

import java.util.Map;

/**
 * A strategy that allows SPI to process {@link HealthCheck} results and enrich and manipulate the result.
 */
public interface HealthCheckResultStrategy {

    /**
     * Processes and allows manipulation of the result from the {@link HealthCheck} invocation.
     *
     * @param check   the invoked health check
     * @param options optional options when invoked the health check
     * @param builder the result builder that builds the health check response, which can be enriched and manipulated by
     *                this strategy.
     */
    void processResult(HealthCheck check, Map<String, Object> options, HealthCheckResultBuilder builder);

}
