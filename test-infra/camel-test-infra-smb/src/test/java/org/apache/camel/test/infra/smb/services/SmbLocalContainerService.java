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

import java.io.InputStream;

import com.github.dockerjava.api.exception.NotFoundException;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.ThrowingFunction;

public class SmbLocalContainerService implements SmbService {
    private static final Logger LOG = LoggerFactory.getLogger(SmbLocalContainerService.class);
    private final SmbContainer container = new SmbContainer();

    public SmbLocalContainerService() {
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

    @Override
    public <T> T copyFileFromContainer(String fileName, ThrowingFunction<InputStream, T> function) {
        try {
            return container.copyFileFromContainer(fileName, function);
        } catch (NotFoundException e) {
            LOG.info("No file found with name {}:", fileName);
            return null;
        }
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

    public String smbFile(String file) {
        return this.container.copyFileFromContainer("data/rw/" + file, IOHelper::loadText);
    }

}
