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

package org.apache.camel.test.infra.hdfs.v2.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.hdfs.v2.common.HDFSProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

public class ContainerLocalHDFSService implements HDFSService, ContainerService<NameNodeContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalHDFSService.class);
    private final NameNodeContainer nameNodeContainer;
    private final DataNodeContainer dataNodeContainer;

    public ContainerLocalHDFSService() {
        Network network = Network.newNetwork();

        nameNodeContainer = initNameNodeContainer(network);
        dataNodeContainer = initDataNodeContainer(network);
    }

    public ContainerLocalHDFSService(NameNodeContainer nameNodeContainer, DataNodeContainer dataNodeContainer) {
        this.nameNodeContainer = nameNodeContainer;
        this.dataNodeContainer = dataNodeContainer;
    }

    protected NameNodeContainer initNameNodeContainer(Network network) {
        return new NameNodeContainer(network);
    }

    protected DataNodeContainer initDataNodeContainer(Network network) {
        return new DataNodeContainer(network);
    }

    @Override
    public String getHDFSHost() {
        return nameNodeContainer.getHost();
    }

    @Override
    public int getPort() {
        return nameNodeContainer.getIpcPort();
    }

    @Override
    public NameNodeContainer getContainer() {
        return nameNodeContainer;
    }

    @Override
    public void registerProperties() {
        System.setProperty(HDFSProperties.HDFS_HOST, getHDFSHost());
        System.getProperty(HDFSProperties.HDFS_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        nameNodeContainer.start();

        registerProperties();

        String hdfsNameNodeWeb = getNameNodeWebAddress();
        LOG.info("HDFS Name node web UI running at address http://{}", hdfsNameNodeWeb);

        dataNodeContainer.start();

        String hdfsDataNodeWeb = getHdfsDataNodeWeb();
        LOG.info("HDFS Data node web UI running at address http://{}", hdfsDataNodeWeb);
        LOG.info("HDFS Data node running at address {}:{}", getHDFSHost(), getPort());
    }

    private String getHdfsDataNodeWeb() {
        return dataNodeContainer.getHost() + ":" + dataNodeContainer.getHttpPort();
    }

    private String getNameNodeWebAddress() {
        return nameNodeContainer.getHost() + ":" + nameNodeContainer.getHttpPort();
    }

    @Override
    public void shutdown() {
        dataNodeContainer.stop();
        nameNodeContainer.stop();
    }
}
