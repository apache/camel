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

package org.apache.camel.test.infra.iggy.services;

import java.util.List;

import com.github.dockerjava.api.model.Ulimit;
import org.apache.camel.test.infra.iggy.common.IggyProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class IggyContainer extends GenericContainer<IggyContainer> {
    public static final String CONTAINER_NAME = "iggy";

    public IggyContainer(String imageName) {
        super(DockerImageName.parse(imageName));
    }

    public static IggyContainer initContainer(String imageName, String networkAlias, boolean fixedPort) {
        class TestInfraIggyContainer extends IggyContainer {
            public TestInfraIggyContainer() {
                super(imageName);
                waitingFor(Wait.forListeningPort());

                addEnv("IGGY_ROOT_USERNAME", IggyProperties.DEFAULT_USERNAME);
                addEnv("IGGY_ROOT_PASSWORD", IggyProperties.DEFAULT_PASSWORD);
                // Bind to all interfaces so the host can connect to the container
                addEnv("IGGY_TCP_ADDRESS", "0.0.0.0:" + IggyProperties.DEFAULT_TCP_PORT);

                // Required capabilities for Iggy container as per docker-compose
                withCreateContainerCmdModifier(cmd -> {
                    cmd.getHostConfig()
                            .withCapAdd(com.github.dockerjava.api.model.Capability.SYS_NICE)
                            .withSecurityOpts(java.util.List.of("seccomp:unconfined"))
                            .withUlimits(List.of(new Ulimit("memlock", -1, -1)));
                });

                if (fixedPort) {
                    addFixedExposedPort(IggyProperties.DEFAULT_TCP_PORT, IggyProperties.DEFAULT_TCP_PORT);
                } else {
                    withNetworkAliases(networkAlias)
                            .withExposedPorts(IggyProperties.DEFAULT_TCP_PORT);
                }
            }
        }

        return new TestInfraIggyContainer();
    }
}
