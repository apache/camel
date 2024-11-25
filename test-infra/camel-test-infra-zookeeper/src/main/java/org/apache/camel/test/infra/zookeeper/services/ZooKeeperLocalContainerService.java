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
package org.apache.camel.test.infra.zookeeper.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.zookeeper.common.ZooKeeperProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperLocalContainerService implements ZooKeeperService, ContainerService<ZooKeeperContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperLocalContainerService.class);

    private final ZooKeeperContainer container;

    public ZooKeeperLocalContainerService() {
        this(LocalPropertyResolver.getProperty(ZooKeeperLocalContainerService.class, ZooKeeperProperties.ZOOKEEPER_CONTAINER));
    }

    public ZooKeeperLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public ZooKeeperLocalContainerService(ZooKeeperContainer container) {
        this.container = container;
    }

    protected ZooKeeperContainer initContainer(String imageName) {
        if (imageName == null) {
            return new ZooKeeperContainer();
        } else {
            return new ZooKeeperContainer(imageName);
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(ZooKeeperProperties.CONNECTION_STRING, getConnectionString());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the ZooKeeper container");
        container.start();

        registerProperties();
        LOG.info("ZooKeeper instance running at {}", getConnectionString());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the ZooKeeper container");
        container.stop();
    }

    @Override
    public ZooKeeperContainer getContainer() {
        return container;
    }

    @Override
    public String getConnectionString() {
        return container.getConnectionString();
    }
}
