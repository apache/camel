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
package org.apache.camel.component.google.secret.manager.unit;

import org.apache.camel.component.google.secret.manager.GoogleSecretManagerComponent;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerEndpoint;
import org.apache.camel.component.google.secret.manager.GoogleSecretManagerOperations;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleSecretManagerConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointConfiguration() throws Exception {
        final String serviceAccountKeyFile = "somefile.json";
        final String project = "project123";
        final GoogleSecretManagerOperations operation = GoogleSecretManagerOperations.createSecret;
        final boolean pojoRequest = false;

        GoogleSecretManagerComponent component = context.getComponent("google-secret-manager",
                GoogleSecretManagerComponent.class);
        GoogleSecretManagerEndpoint endpoint = (GoogleSecretManagerEndpoint) component.createEndpoint(String.format(
                "google-secret-manager://%s?serviceAccountKey=%s&operation=%s&pojoRequest=%s",
                project, serviceAccountKeyFile, operation.name(), pojoRequest));

        assertEquals(endpoint.getConfiguration().getServiceAccountKey(), serviceAccountKeyFile);
        assertEquals(endpoint.getConfiguration().getProject(), project);
        assertEquals(endpoint.getConfiguration().getOperation(), operation);
        assertEquals(endpoint.getConfiguration().isPojoRequest(), pojoRequest);
    }
}
