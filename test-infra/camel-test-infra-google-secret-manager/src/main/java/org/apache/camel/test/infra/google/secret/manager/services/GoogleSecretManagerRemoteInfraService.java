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

package org.apache.camel.test.infra.google.secret.manager.services;

import org.apache.camel.test.infra.google.secret.manager.common.GoogleSecretManagerProperties;

/**
 * The Secret Manager service of a real Google Cloud project, the one of the {@code google.secret.manager.project.id}
 * system property, which the clients reach with the application default credentials, such as the key of the
 * {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable.
 */
public class GoogleSecretManagerRemoteInfraService implements GoogleSecretManagerInfraService {
    private static final String DEFAULT_ENDPOINT = "secretmanager.googleapis.com:443";

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

    @Override
    public String projectId() {
        return System.getProperty(GoogleSecretManagerProperties.PROJECT_ID);
    }

    @Override
    public String endpoint() {
        return System.getProperty(GoogleSecretManagerProperties.ENDPOINT, DEFAULT_ENDPOINT);
    }
}
