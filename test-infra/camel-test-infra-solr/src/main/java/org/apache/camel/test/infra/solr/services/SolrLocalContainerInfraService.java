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

@InfraService(service = SolrInfraService.class, serviceAlias = { "solr" })
public class SolrLocalContainerInfraService implements SolrInfraService, ContainerService<SolrContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(SolrLocalContainerInfraService.class);
    private final SolrContainer container;

    public SolrLocalContainerInfraService() {
        container = SolrContainer.initContainer(SolrContainer.CONTAINER_NAME, isCloudMode());
    }

    @Override
    public void registerProperties() {
        System.setProperty(SolrProperties.SERVICE_ADDRESS, getSolrBaseUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start solr container");
        container.withStartupAttempts(5);
        container.start();

        registerProperties();
        LOG.info("Solr instance running at {}", getSolrBaseUrl());
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

    protected String getHost() {
        return container.getHost();
    }

    protected int getPort() {
        return container.getMappedPort(SolrProperties.DEFAULT_PORT);
    }

    @Override
    public String getSolrBaseUrl() {
        return String.format("http://%s:%d/solr", getHost(), getPort());
    }

    @Override
    public boolean isCloudMode() {
        return false;
    }
}
