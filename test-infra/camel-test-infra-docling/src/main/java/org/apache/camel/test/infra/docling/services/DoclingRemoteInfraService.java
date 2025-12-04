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

package org.apache.camel.test.infra.docling.services;

import org.apache.camel.test.infra.docling.common.DoclingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote Docling infrastructure service for testing with external Docling instances
 */
public class DoclingRemoteInfraService implements DoclingInfraService {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingRemoteInfraService.class);

    private final String doclingServerUrl;

    public DoclingRemoteInfraService() {
        this(System.getProperty(DoclingProperties.DOCLING_SERVER_URL));
    }

    public DoclingRemoteInfraService(String doclingServerUrl) {
        this.doclingServerUrl = doclingServerUrl;
    }

    @Override
    public void registerProperties() {
        System.setProperty(DoclingProperties.DOCLING_SERVER_URL, doclingServerUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Using remote Docling instance at {}", doclingServerUrl());
        registerProperties();
    }

    @Override
    public void shutdown() {
        LOG.info("Remote Docling service shutdown (no-op)");
    }

    @Override
    public String doclingServerUrl() {
        return doclingServerUrl;
    }
}
