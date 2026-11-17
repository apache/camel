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

package org.apache.camel.test.infra.google.firestore.services;

import org.apache.camel.test.infra.google.firestore.common.GoogleFirestoreProperties;

/**
 * The Firestore service of a real Google Cloud project, the one of the {@code google.firestore.projectId} system
 * property, which the integration tests reach with the service account key of the
 * {@code google.firestore.serviceAccountKey} system property.
 */
public class GoogleFirestoreRemoteInfraService implements GoogleFirestoreInfraService {
    private static final String DEFAULT_ENDPOINT = "firestore.googleapis.com:443";

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
        return System.getProperty(GoogleFirestoreProperties.PROJECT_ID);
    }

    @Override
    public String endpoint() {
        return System.getProperty(GoogleFirestoreProperties.ENDPOINT, DEFAULT_ENDPOINT);
    }
}
