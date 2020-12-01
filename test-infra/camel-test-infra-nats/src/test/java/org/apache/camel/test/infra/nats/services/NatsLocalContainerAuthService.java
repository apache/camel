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

import org.apache.camel.test.infra.nats.common.NatsProperties;
import org.testcontainers.containers.wait.strategy.Wait;

public class NatsLocalContainerAuthService extends NatsLocalContainerService {
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";

    protected void initContainer(String imageName) {
        super.initContainer(imageName);

        getContainer()
                .waitingFor(Wait.forLogMessage(".*Server.*is.*ready.*", 1))
                .withCommand("-DV", "--user", USERNAME, "--pass", PASSWORD);
    }

    @Override
    public void registerProperties() {
        super.registerProperties();

        System.setProperty(NatsProperties.ACCESS_USERNAME, USERNAME);
        System.setProperty(NatsProperties.ACCESS_PASSWORD, PASSWORD);
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%s@%s:%d", USERNAME, PASSWORD, getHost(), getPort());
    }
}
