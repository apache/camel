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
package org.apache.camel.test.infra.qdrant.services;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class QdrantContainer extends GenericContainer<QdrantContainer> {
    public static final int HTTP_PORT = 6333;
    public static final int GRPC_PORT = 6334;

    public QdrantContainer(DockerImageName imageName) {
        super(imageName);
    }

    @Override
    protected void configure() {
        super.configure();

        withExposedPorts(HTTP_PORT, GRPC_PORT);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(QdrantContainer.class)));
        waitingFor(Wait.forLogMessage(".*Actix runtime found; starting in Actix runtime.*", 1));
    }

    public String getGrpcHost() {
        return getHost();
    }

    public int getGrpcPort() {
        return getMappedPort(GRPC_PORT);
    }

    public String getHttpHost() {
        return getHost();
    }

    public int getHttpPort() {
        return getMappedPort(HTTP_PORT);
    }
}
