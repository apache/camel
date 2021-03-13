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
package org.apache.camel.test.infra.hbase.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseLocalContainerService implements HBaseService, ContainerService<HBaseContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseLocalContainerService.class);

    private final HBaseContainer container;

    public HBaseLocalContainerService() {
        container = initContainer();
    }

    public HBaseLocalContainerService(HBaseContainer container) {
        this.container = container;
    }

    protected HBaseContainer initContainer() {
        return new HBaseContainer();
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the HBase container");
        container.start();

        registerProperties();
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the HBase container");
        container.stop();
    }

    @Override
    public HBaseContainer getContainer() {
        return container;
    }

    @Override
    public Configuration getConfiguration() {
        return HBaseContainer.defaultConf();
    }
}
