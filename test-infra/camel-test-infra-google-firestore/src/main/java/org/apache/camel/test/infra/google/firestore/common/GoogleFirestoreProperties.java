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

package org.apache.camel.test.infra.google.firestore.common;

/**
 * The system properties of the Firestore service: the project id and the service account key are the ones that the
 * integration tests of camel-google-firestore have always taken.
 */
public final class GoogleFirestoreProperties {
    public static final String PROJECT_ID = "google.firestore.projectId";
    public static final String SERVICE_ACCOUNT_KEY = "google.firestore.serviceAccountKey";
    public static final String DATABASE_ID = "google.firestore.databaseId";
    public static final String ENDPOINT = "google.firestore.endpoint";

    private GoogleFirestoreProperties() {

    }
}
