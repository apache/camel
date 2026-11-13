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

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.firestore.GoogleFirestoreComponent;
import org.apache.camel.component.google.firestore.GoogleFirestoreConfiguration;
import org.apache.camel.spi.OnCamelContextStopped;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.test.infra.google.firestore.common.GoogleFirestoreProperties;
import org.apache.camel.test.infra.google.firestore.services.GoogleFirestoreService;
import org.apache.camel.test.infra.google.firestore.services.GoogleFirestoreServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Base support class for Google Firestore integration tests.
 *
 * <p>
 * The tests run against the Firestore service of Floci GCP. To run them against the Firestore of a Google Cloud
 * project, provide the following system properties:
 * </p>
 * <ul>
 * <li>{@code google.instance.type=remote}</li>
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
 *     -Dgoogle.instance.type=remote \
 *     -Dgoogle.firestore.serviceAccountKey=/path/to/service-account.json \
 *     -Dgoogle.firestore.projectId=my-project-id
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class GoogleFirestoreITSupport extends CamelTestSupport {

    @RegisterExtension
    public static GoogleFirestoreService service = GoogleFirestoreServiceFactory.createSingletonService();

    /**
     * Test collection name with unique suffix to avoid conflicts between test runs.
     */
    protected static final String TEST_COLLECTION = "camel-test-" + UUID.randomUUID().toString().substring(0, 8);

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
        configuration.setProjectId(service.projectId());

        if (isRemote()) {
            configuration.setServiceAccountKey(
                    "file:" + System.getProperty(GoogleFirestoreProperties.SERVICE_ACCOUNT_KEY));
            String databaseId = System.getProperty(GoogleFirestoreProperties.DATABASE_ID);
            if (databaseId != null) {
                configuration.setDatabaseId(databaseId);
            }
        } else {
            // Floci GCP is reached as an emulator: in plain text, with any credentials
            Firestore client = FirestoreOptions.newBuilder()
                    .setProjectId(service.projectId())
                    .setEmulatorHost(service.endpoint())
                    .build()
                    .getService();
            configuration.setFirestoreClient(client);
            // the endpoints do not close a client that they did not build: it goes with the context
            context.addLifecycleStrategy(LifecycleStrategySupport.adapt((OnCamelContextStopped) stopped -> {
                try {
                    client.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }

        GoogleFirestoreComponent component = new GoogleFirestoreComponent(context);
        component.setConfiguration(configuration);

        context.addComponent("google-firestore", component);
    }

    // the service is the one of a Google Cloud project with -Dgoogle.instance.type=remote, and the one of Floci GCP
    // otherwise
    private static boolean isRemote() {
        return "remote".equals(System.getProperty("google.instance.type"));
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
