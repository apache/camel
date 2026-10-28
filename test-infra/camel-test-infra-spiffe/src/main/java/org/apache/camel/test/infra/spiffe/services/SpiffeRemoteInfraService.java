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

import org.apache.camel.test.infra.spiffe.common.SpiffeProperties;

/**
 * A SPIFFE service backed by an already-running SPIRE Workload API, configured through system properties.
 */
public class SpiffeRemoteInfraService implements SpiffeInfraService {

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public String getWorkloadApiSocketPath() {
        return System.getProperty(SpiffeProperties.SPIFFE_SOCKET_PATH);
    }

    @Override
    public String getTrustDomain() {
        String trustDomain = System.getProperty(SpiffeProperties.SPIFFE_TRUST_DOMAIN);
        return trustDomain != null ? trustDomain : SpiffeLocalContainerInfraService.TRUST_DOMAIN;
    }

    @Override
    public String getWorkloadSpiffeId() {
        String workloadId = System.getProperty(SpiffeProperties.SPIFFE_WORKLOAD_ID);
        return workloadId != null ? workloadId : SpiffeLocalContainerInfraService.WORKLOAD_SPIFFE_ID;
    }
}
