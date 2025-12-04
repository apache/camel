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

package org.apache.camel.test.infra.pinecone.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class PineconeIndexContainer extends GenericContainer<PineconeIndexContainer> {
    public static final String CONTAINER_NAME = "pinecone-index";
    public static final int CLIENT_PORT = 5080;

    private static final Logger LOGGER = LoggerFactory.getLogger(PineconeIndexContainer.class);

    private static final DockerImageName DEFAULT_IMAGE_NAME =
            DockerImageName.parse("ghcr.io/pinecone-io/pinecone-index");

    public PineconeIndexContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName), false);
    }

    public PineconeIndexContainer(DockerImageName dockerImageName, boolean fixedPort) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withEnv("PINECONE_HOST", "localhost");
        withEnv("VECTOR_TYPE", "dense");
        withEnv("DIMENSION", "6");
        withEnv("INDEX_TYPE", "serverless");
        withEnv("METRIC", "cosine");
        withEnv("PORT", String.valueOf(CLIENT_PORT));
        if (fixedPort) {
            addFixedExposedPort(CLIENT_PORT, CLIENT_PORT);
        } else {
            withExposedPorts(CLIENT_PORT);
        }
    }

    public String getEndpoint() {
        return "http://" + getConnectionString();
    }

    public String getConnectionString() {
        return String.format("%s:%d", getHost(), getMappedPort(CLIENT_PORT));
    }
}
