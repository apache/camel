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
package org.apache.camel.component.google.firestore.integration;

import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.component.google.firestore.GoogleFirestoreComponent;
import org.apache.camel.component.google.firestore.GoogleFirestoreConfiguration;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;

/**
 * Base support class for Google Firestore integration tests.
 *
 * <p>
 * To run integration tests, provide the following system properties:
 * </p>
 * <ul>
 * <li>{@code google.firestore.serviceAccountKey} - Path to the service account JSON key file</li>
 * <li>{@code google.firestore.projectId} - Google Cloud project ID</li>
 * <li>{@code google.firestore.databaseId} - (Optional) Firestore database ID, defaults to "(default)"</li>
 * </ul>
 *
 * <p>
 * Example Maven command:
 * </p>
 *
 * <pre>
 * mvn verify -pl components/camel-google/camel-google-firestore \
 *     -Dgoogle.firestore.serviceAccountKey=/path/to/service-account.json \
 *     -Dgoogle.firestore.projectId=my-project-id
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class GoogleFirestoreITSupport extends CamelTestSupport {

    protected static final String SERVICE_ACCOUNT_KEY = System.getProperty("google.firestore.serviceAccountKey");
    protected static final String PROJECT_ID = System.getProperty("google.firestore.projectId");
    protected static final String DATABASE_ID = System.getProperty("google.firestore.databaseId");

    /**
     * Test collection name with unique suffix to avoid conflicts between test runs.
     */
    protected static final String TEST_COLLECTION = "camel-test-" + UUID.randomUUID().toString().substring(0, 8);

    /**
     * Checks if credentials are available to run integration tests.
     *
     * @return true if service account key and project ID are provided
     */
    public static boolean hasCredentials() {
        return SERVICE_ACCOUNT_KEY != null && !SERVICE_ACCOUNT_KEY.isEmpty()
                && PROJECT_ID != null && !PROJECT_ID.isEmpty();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        addFirestoreComponent(context);
        return context;
    }

    /**
     * Configures and adds the Google Firestore component to the Camel context.
     *
     * @param context the Camel context
     */
    protected void addFirestoreComponent(CamelContext context) {
        GoogleFirestoreConfiguration configuration = new GoogleFirestoreConfiguration();

        if (SERVICE_ACCOUNT_KEY != null) {
            configuration.setServiceAccountKey("file:" + SERVICE_ACCOUNT_KEY);
        }
        if (PROJECT_ID != null) {
            configuration.setProjectId(PROJECT_ID);
        }
        if (DATABASE_ID != null) {
            configuration.setDatabaseId(DATABASE_ID);
        }

        GoogleFirestoreComponent component = new GoogleFirestoreComponent(context);
        component.setConfiguration(configuration);

        context.addComponent("google-firestore", component);
    }

    /**
     * Returns the test collection name.
     *
     * @return the collection name used for testing
     */
    protected String getTestCollection() {
        return TEST_COLLECTION;
    }

    /**
     * Generates a unique document ID for testing.
     *
     * @return a unique document ID
     */
    protected String generateDocumentId() {
        return "doc-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
