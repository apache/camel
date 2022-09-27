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

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.elasticsearch.common.ElasticSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class ElasticSearchLocalContainerService implements ElasticSearchService, ContainerService<ElasticsearchContainer> {
    public static final String DEFAULT_ELASTIC_SEARCH_CONTAINER = "docker.elastic.co/elasticsearch/elasticsearch-oss:7.17.1";

    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchLocalContainerService.class);
    private static final int ELASTIC_SEARCH_PORT = 9200;

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
        return new ElasticsearchContainer(imageName);
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
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the ElasticSearch container");
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
}
