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

package org.apache.camel.test.infra.google.storage.services;

import org.apache.camel.test.infra.google.storage.common.GoogleStorageProperties;

/**
 * The Cloud Storage service of a real Google Cloud project, which the clients reach with the application default
 * credentials, such as the key of the {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable.
 */
public class GoogleStorageRemoteInfraService implements GoogleStorageInfraService {
    private static final String DEFAULT_ENDPOINT = "https://storage.googleapis.com";

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    /**
     * The id of the project of the {@code google.storage.project.id} system property, if any: the clients otherwise
     * take the one of the application default credentials
     */
    @Override
    public String projectId() {
        return System.getProperty(GoogleStorageProperties.PROJECT_ID);
    }

    @Override
    public String endpoint() {
        return System.getProperty(GoogleStorageProperties.ENDPOINT, DEFAULT_ENDPOINT);
    }
}
