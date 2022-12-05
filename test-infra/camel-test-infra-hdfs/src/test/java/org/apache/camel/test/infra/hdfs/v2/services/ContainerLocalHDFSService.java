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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerLocalHDFSService implements HDFSService, ContainerService<HDFSContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalHDFSService.class);
    private final HDFSContainer container;

    public ContainerLocalHDFSService() {
        container = new HDFSContainer();
    }

    @Override
    public String getHDFSHost() {
        return container.getHost();
    }

    @Override
    public int getPort() {
        return container.getPort();
    }

    @Override
    public HDFSContainer getContainer() {
        return container;
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the HDFS container");
        container.start();

        registerProperties();
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the HDFS container");
        container.stop();
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }
}
