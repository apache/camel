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
package org.apache.camel.test.infra.nats.services;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class NatsLocalContainerTLSAuthService extends NatsLocalContainerService {
    /*Certificates used for tests with TLS authentication come from:
     *https://github.com/nats-io/jnats/tree/master/src/test/resources */
    protected GenericContainer initContainer(String imageName, String containerName) {
        GenericContainer container = super.initContainer(imageName, containerName);

        container
                .waitingFor(Wait.forLogMessage(".*Server.*is.*ready.*", 1))
                .withClasspathResourceMapping("org/apache/camel/test/infra/nats/services", "/nats", BindMode.READ_ONLY)
                .withCommand("--tls",
                        "--tlscert=/nats/server.pem",
                        "--tlskey=/nats/key.pem",
                        "--tlsverify",
                        "--tlscacert=/nats/ca.pem");

        return container;
    }
}
