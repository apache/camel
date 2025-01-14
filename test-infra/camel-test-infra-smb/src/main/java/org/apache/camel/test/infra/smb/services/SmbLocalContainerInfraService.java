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
package org.apache.camel.test.infra.smb.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(service = SmbLocalContainerInfraService.class,
              description = "SAMBA File Server",
              serviceAlias = "smb")
public class SmbLocalContainerInfraService implements SmbInfraService {
    protected static final Logger LOG = LoggerFactory.getLogger(SmbLocalContainerInfraService.class);
    protected final SmbContainer container = new SmbContainer();

    public SmbLocalContainerInfraService() {
    }

    @Override
    public String address() {
        return container.getHost() + ":" + container.getPort();
    }

    @Override
    public String shareName() {
        return container.getShare();
    }

    @Override
    public String userName() {
        return container.getUser();
    }

    @Override
    public String password() {
        return container.getPassword();
    }

    public String smbFile(String file) {
        return this.container.copyFileFromContainer("data/rw/" + file, IOHelper::loadText);
    }

    @Override
    public void registerProperties() {
    }

    @Override
    public void initialize() {
        container.start();
        registerProperties();

        LOG.info("SMB host running at address {}:", address());
    }

    @Override
    public void shutdown() {
    }
}
