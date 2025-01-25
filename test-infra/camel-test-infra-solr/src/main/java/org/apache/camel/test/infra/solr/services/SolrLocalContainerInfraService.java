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
package org.apache.camel.test.infra.solr.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.solr.common.SolrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(service = SolrInfraService.class,
              description = "Apache Solr is a Search Platform",
              serviceAlias = { "solr" })
public class SolrLocalContainerInfraService implements SolrInfraService, ContainerService<SolrContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(SolrLocalContainerInfraService.class);
    private final SolrContainer container;

    public SolrLocalContainerInfraService() {
        container = SolrContainer.initContainer(SolrContainer.CONTAINER_NAME);
    }

    @Override
    public void registerProperties() {
        System.setProperty(SolrProperties.SOLR_HOST, getSolrHost());
        System.setProperty(SolrProperties.SOLR_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start solr container");
        container.withStartupAttempts(5);
        container.start();

        registerProperties();
        LOG.info("Solr instance running at {}", getSolrBaseUrl());
    }

    private String getSolrBaseUrl() {
        return String.format("http://%s/solr", getHttpHostAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Solr container");
        container.stop();
    }

    @Override
    public SolrContainer getContainer() {
        return container;
    }

    public String getSolrHost() {
        return container.getHost();
    }

    public int getPort() {
        return container.getMappedPort(SolrProperties.DEFAULT_PORT);
    }

}
