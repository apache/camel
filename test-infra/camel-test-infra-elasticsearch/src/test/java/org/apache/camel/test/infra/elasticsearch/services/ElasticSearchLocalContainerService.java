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

package org.apache.camel.test.infra.elasticsearch.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.elasticsearch.common.ElasticSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ElasticSearchLocalContainerService implements ElasticSearchService, ContainerService<ElasticsearchContainer> {
    public static final String DEFAULT_ELASTIC_SEARCH_CONTAINER = "docker.elastic.co/elasticsearch/elasticsearch:8.8.2";
    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchLocalContainerService.class);
    private static final int ELASTIC_SEARCH_PORT = 9200;
    private static final String USER_NAME = "elastic";
    private static final String PASSWORD = "s3cret";
    private Path certPath;
    private SSLContext sslContext;
    private final ElasticsearchContainer container;

    public ElasticSearchLocalContainerService() {
        this(System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_CONTAINER, DEFAULT_ELASTIC_SEARCH_CONTAINER));
    }

    public ElasticSearchLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public ElasticSearchLocalContainerService(ElasticsearchContainer container) {
        this.container = container;
    }

    protected ElasticsearchContainer initContainer(String imageName) {
        ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(imageName)
                .withPassword(PASSWORD);
        // Increase the timeout from 60 seconds to 90 seconds to ensure that it will be long enough
        // on the build pipeline
        elasticsearchContainer.setWaitStrategy(
                new LogMessageWaitStrategy()
                        .withRegEx(".*(\"message\":\\s?\"started[\\s?|\"].*|] started\n$)")
                        .withStartupTimeout(Duration.ofSeconds(90)));
        return elasticsearchContainer;

    }

    @Override
    public int getPort() {
        return container.getMappedPort(ELASTIC_SEARCH_PORT);
    }

    @Override
    public String getElasticSearchHost() {
        return container.getHost();
    }

    @Override
    public String getHttpHostAddress() {
        return container.getHttpHostAddress();
    }

    @Override
    public void registerProperties() {
        System.setProperty(ElasticSearchProperties.ELASTIC_SEARCH_HOST, getElasticSearchHost());
        System.setProperty(ElasticSearchProperties.ELASTIC_SEARCH_PORT, String.valueOf(getPort()));
        getContainer().caCertAsBytes().ifPresent(content -> {
            try {
                certPath = Files.createTempFile("http_ca", ".crt");
                Files.write(certPath, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        sslContext = getContainer().createSslContextFromCa();
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the ElasticSearch container");
        ContainerEnvironmentUtil.configureContainerStartup(container, ElasticSearchProperties.ELASTIC_SEARCH_CONTAINER_STARTUP,
                2);

        container.start();

        registerProperties();
        LOG.info("ElasticSearch instance running at {}", getHttpHostAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the ElasticSearch container");
        container.stop();
    }

    @Override
    public ElasticsearchContainer getContainer() {
        return container;
    }

    @Override
    public Optional<String> getCertificatePath() {
        return Optional.ofNullable(certPath).map(Objects::toString);
    }

    @Override
    public Optional<SSLContext> getSslContext() {
        return Optional.ofNullable(sslContext);
    }

    @Override
    public String getUsername() {
        return USER_NAME;
    }

    @Override
    public String getPassword() {
        return PASSWORD;
    }
}
