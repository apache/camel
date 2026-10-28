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
package org.apache.camel.test.infra.spiffe.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Test infra service for SPIFFE/SPIRE: a SPIRE server plus an agent exposing the Workload API, so a test can exercise
 * camel-spiffe against a real Workload API endpoint rather than a mock.
 */
public interface SpiffeInfraService extends InfrastructureService {

    /**
     * The Workload API endpoint, as a {@code unix://} URI, for the camel-spiffe {@code spiffeSocketPath} option (or the
     * {@code SPIFFE_ENDPOINT_SOCKET} environment variable).
     */
    String getWorkloadApiSocketPath();

    /**
     * The trust domain the SPIRE server was configured with (for example {@code example.org}).
     */
    String getTrustDomain();

    /**
     * The SPIFFE ID of the workload SVID the agent issues to the test process (for example
     * {@code spiffe://example.org/workload}).
     */
    String getWorkloadSpiffeId();
}
