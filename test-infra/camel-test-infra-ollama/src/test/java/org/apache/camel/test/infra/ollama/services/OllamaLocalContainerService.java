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
package org.apache.camel.test.infra.ollama.services;

import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.ollama.commons.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

public class OllamaLocalContainerService implements OllamaService, ContainerService<OllamaContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaLocalContainerService.class);

    public static final String CONTAINER_PORT = LocalPropertyResolver.getProperty(
            OllamaLocalContainerService.class, OllamaProperties.PORT);

    public static final String CONTAINER_NAME = LocalPropertyResolver.getProperty(
            OllamaLocalContainerService.class, OllamaProperties.CONTAINER);

    public static String OLLAMA_MODEL = LocalPropertyResolver.getProperty(
            OllamaLocalContainerService.class, OllamaProperties.MODEL);

    public static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", CONTAINER_NAME, OLLAMA_MODEL);

    private final DockerImageName dockerImageName;

    private final OllamaContainer container;

    private final Integer port;

    public OllamaLocalContainerService() {
        port = Integer.valueOf(CONTAINER_PORT);

        dockerImageName = resolveImageName();

        container = initContainer();
    }

    protected OllamaContainer initContainer() {
        return new OllamaContainer(dockerImageName, port, OLLAMA_MODEL, LOCAL_OLLAMA_IMAGE);
    }

    protected DockerImageName resolveImageName() {
        DockerImageName dockerImageName = DockerImageName.parse(CONTAINER_NAME);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(LOCAL_OLLAMA_IMAGE).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(LOCAL_OLLAMA_IMAGE);
    }

    @Override
    public String getBaseUrl() {
        return "http://" + container.getHost() + ":" + container.getMappedPort(port);
    }

    @Override
    public String getModel() {
        return OLLAMA_MODEL;
    }

    @Override
    public void registerProperties() {
        System.setProperty(OllamaProperties.PORT, String.valueOf(port));
        System.setProperty(OllamaProperties.BASE_URL, String.valueOf(getBaseUrl()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Ollama container");
        container.start();

        registerProperties();
        LOG.info("Ollama instance running at {}", port);
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Ollama container");
        container.stop();
    }

    @Override
    public OllamaContainer getContainer() {
        return container;
    }
}
