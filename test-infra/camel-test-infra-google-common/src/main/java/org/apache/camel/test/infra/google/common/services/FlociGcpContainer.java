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

package org.apache.camel.test.infra.google.common.services;

import java.time.Duration;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A container running Floci GCP (https://github.com/floci-io/floci-gcp), a local Google Cloud emulator. It serves the
 * gRPC and the REST APIs of the Google Cloud services on one port, and accepts any project id.
 */
public class FlociGcpContainer extends GenericContainer<FlociGcpContainer> {
    public static final int SERVICE_PORT = 4588;

    public FlociGcpContainer(String imageName, boolean fixedPort) {
        super(imageName);

        ContainerEnvironmentUtil.configurePort(this, fixedPort, SERVICE_PORT);

        waitingFor(Wait.forHttp("/health")
                .forPort(SERVICE_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    /**
     * The address of the emulator, for example {@code http://localhost:32768}
     */
    public String serviceAddress() {
        return String.format("http://%s:%d", getHost(), getMappedPort(SERVICE_PORT));
    }
}
