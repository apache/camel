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
package org.apache.camel.test.infra.jaeger.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.jaeger.common.JaegerProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class JaegerContainer extends GenericContainer<JaegerContainer> {
    public static final String CONTAINER_NAME = "jaeger";

    public JaegerContainer() {
        super(LocalPropertyResolver.getProperty(JaegerLocalContainerInfraService.class, JaegerProperties.JAEGER_CONTAINER));

        this.withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(
                        JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT,
                        JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT,
                        JaegerProperties.DEFAULT_QUERY_UI_PORT)
                .waitingFor(Wait.forHttp("/").forPort(JaegerProperties.DEFAULT_QUERY_UI_PORT));
    }

    public JaegerContainer(String imageName) {
        super(DockerImageName.parse(imageName));

        this.withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(
                        JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT,
                        JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT,
                        JaegerProperties.DEFAULT_QUERY_UI_PORT)
                .waitingFor(Wait.forHttp("/").forPort(JaegerProperties.DEFAULT_QUERY_UI_PORT));
    }

    public JaegerContainer withFixedPort(int hostPort, int containerPort) {
        addFixedExposedPort(hostPort, containerPort);
        return this;
    }

    @SuppressWarnings("resource")
    // NOTE: the object must be closed by the client.
    public static JaegerContainer initContainer(String imageName, String networkAlias) {
        return new JaegerContainer(imageName) // NOSONAR
                .withNetworkAliases(networkAlias)
                .withExposedPorts(
                        JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT,
                        JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT,
                        JaegerProperties.DEFAULT_QUERY_UI_PORT)
                .waitingFor(Wait.forHttp("/").forPort(JaegerProperties.DEFAULT_QUERY_UI_PORT));
    }
}
