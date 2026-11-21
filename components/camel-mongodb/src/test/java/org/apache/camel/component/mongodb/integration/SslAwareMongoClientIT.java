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
package org.apache.camel.component.mongodb.integration;

import com.mongodb.MongoSecurityException;
import com.mongodb.ReadPreference;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mongodb.MongoDbEndpoint;
import org.apache.camel.component.mongodb.SslAwareMongoClient;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client the MongoDB Kamelets create as a bean: it logs in with a password that has characters a connection string
 * must percent-encode, and reads with its read preference.
 */
// public: the ContextFixture of the test-infra is called by reflection
public class SslAwareMongoClientIT extends AbstractMongoDbITSupport {

    private static final String KAMELET_USER = "kamelet-user";
    private static final String KAMELET_PASSWORD = "p@ss:w/o?r%d %41";

    private static SslAwareMongoClient kameletClient;

    private static SslAwareMongoClient client(String password) {
        SslAwareMongoClient client = new SslAwareMongoClient();
        client.setHosts(service.getConnectionAddress());
        client.setUsername(KAMELET_USER);
        client.setPassword(password);
        client.setSsl(false);
        return client;
    }

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) throws Exception {
        super.configureContext(context);
        // a user defined in the admin database, as the Kamelets document
        createAuthorizationUser("admin", KAMELET_USER, KAMELET_PASSWORD);

        kameletClient = client(KAMELET_PASSWORD);
        kameletClient.setReadPreference("SECONDARY_PREFERRED");
        context.getRegistry().bind("kameletClient", kameletClient);
    }

    @AfterAll
    static void closeKameletClient() {
        // the endpoint does not close a client it did not create
        if (kameletClient != null) {
            kameletClient.close();
            kameletClient = null;
        }
    }

    @Test
    void readsWithTheReadPreferenceOfTheClient() {
        testCollection.insertOne(new Document("a", 1));
        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:kameletClient?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count",
                MongoDbEndpoint.class);

        Long count = template.requestBody(endpoint, "{}", Long.class);

        assertThat(count).isEqualTo(1L);
        assertThat(endpoint.getMongoCollection().getReadPreference()).isEqualTo(ReadPreference.secondaryPreferred());
    }

    @Test
    void wrongPasswordFailsToLogIn() {
        // the server checks the credentials, so the test above logged in with the password as it is
        try (SslAwareMongoClient client = client("p@ss:w/o?r%d A")) {
            assertThatThrownBy(() -> client.getDatabase(dbName).getCollection(testCollectionName).countDocuments())
                    .isInstanceOf(MongoSecurityException.class);
        }
    }
}
