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
package org.apache.camel.test.infra.torchserve.services;

import org.apache.camel.test.infra.torchserve.common.TorchServeProperties;

public class TorchServeRemoteInfraService implements TorchServeInfraService {

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
    public int inferencePort() {
        String value = System.getProperty(TorchServeProperties.TORCHSERVE_INFERENCE_PORT, "8080");
        return Integer.parseInt(value);
    }

    @Override
    public int managementPort() {
        String value = System.getProperty(TorchServeProperties.TORCHSERVE_MANAGEMENT_PORT, "8081");
        return Integer.parseInt(value);
    }

    @Override
    public int metricsPort() {
        String value = System.getProperty(TorchServeProperties.TORCHSERVE_METRICS_PORT, "8082");
        return Integer.parseInt(value);
    }
}
