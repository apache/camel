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

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * A Firestore service: the one of Floci GCP, or the one of a real Google Cloud project.
 */
public interface GoogleFirestoreInfraService extends InfrastructureService {

    /**
     * The id of the project
     */
    String projectId();

    /**
     * The address of the Firestore API, for example {@code localhost:32768} for Floci GCP, which the clients reach in
     * plain text as an emulator, or {@code firestore.googleapis.com:443} for Google Cloud
     */
    String endpoint();
}
