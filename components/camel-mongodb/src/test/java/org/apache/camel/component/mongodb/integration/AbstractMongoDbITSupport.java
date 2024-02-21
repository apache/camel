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

import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mongodb.CamelMongoDbException;
import org.apache.camel.component.mongodb.MongoDbComponent;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.mongodb.services.MongoDBService;
import org.apache.camel.test.infra.mongodb.services.MongoDBServiceFactory;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractMongoDbITSupport implements ConfigurableContext {

    @Order(1)
    @RegisterExtension
    public static MongoDBService service = MongoDBServiceFactory.createSingletonService();

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    protected static final String SCHEME = "mongodb";
    protected static final String USER = "test-user";
    protected static final String PASSWORD = "test-pwd";

    protected static MongoClient mongo;
    protected static MongoDatabase db;
    protected static MongoCollection<Document> testCollection;
    protected static MongoCollection<Document> dynamicCollection;

    protected static String dbName = "test";
    protected static String testCollectionName;
    protected static String dynamicCollectionName;

    protected CamelContext context;
    protected ProducerTemplate template;

    @BeforeEach
    public void setupContextAndTemplates() {
        template = contextExtension.getProducerTemplate();
        context = contextExtension.getContext();
    }

    protected void doPreSetup() throws Exception {
        mongo = MongoClients.create(service.getReplicaSetUrl());

        db = mongo.getDatabase(dbName);
    }

    protected void doPostSetup() {
        refresh();
    }

    protected void refresh() {
        // Refresh the test collection - drop it and recreate it. We don't do
        // this for the database because MongoDB would create large
        // store files each time
        testCollectionName = "camelTest";
        testCollection = db.getCollection(testCollectionName, Document.class);
        testCollection.drop();
        testCollection = db.getCollection(testCollectionName, Document.class);

        dynamicCollectionName = testCollectionName.concat("Dynamic");
        dynamicCollection = db.getCollection(dynamicCollectionName, Document.class);
        dynamicCollection.drop();
        dynamicCollection = db.getCollection(dynamicCollectionName, Document.class);
    }

    @AfterEach
    public void tearDown() {
        if (testCollection != null) {
            testCollection.drop();
        }

        if (dynamicCollection != null) {
            dynamicCollection.drop();
        }
    }

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) throws Exception {
        doPreSetup();

        context.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", mongo);

        doPostSetup();
    }

    /**
     * Useful to simulate the presence of an authenticated user with name {@value #USER} and password {@value #PASSWORD}
     */
    protected void createAuthorizationUser() {
        createAuthorizationUser("admin", USER, PASSWORD);
    }

    protected void createAuthorizationUser(String database, String user, String password) {
        MongoDatabase adminDb = mongo.getDatabase("admin");
        MongoCollection<Document> usersCollection = adminDb.getCollection("system.users");
        if (usersCollection.countDocuments(new Document("user", user)) == 0) {
            MongoDatabase db = mongo.getDatabase(database);
            Map<String, Object> commandArguments = new LinkedHashMap<>();
            commandArguments.put("createUser", user);
            commandArguments.put("pwd", password);
            String[] roles = { "readWrite" };
            commandArguments.put("roles", roles);
            BasicDBObject command = new BasicDBObject(commandArguments);
            db.runCommand(command);
        }
    }

    protected void pumpDataIntoTestCollection() {
        // there should be 100 of each
        String[] scientists
                = { "Einstein", "Darwin", "Copernicus", "Pasteur", "Curie", "Faraday", "Newton", "Bohr", "Galilei", "Maxwell" };
        for (int i = 1; i <= 1000; i++) {
            int index = i % scientists.length;
            Formatter f = new Formatter();
            String doc
                    = f.format("{\"_id\":\"%d\", \"scientist\":\"%s\", \"fixedField\": \"fixedValue\"}", i, scientists[index])
                            .toString();
            IOHelper.close(f);
            testCollection.insertOne(Document.parse(doc));
        }
        assertEquals(1000L, testCollection.countDocuments(), "Data pumping of 1000 entries did not complete entirely");
    }

    protected CamelMongoDbException extractAndAssertCamelMongoDbException(Object result, String message) {
        Throwable exc = ((CamelExecutionException) result).getCause();
        assertTrue(exc instanceof CamelMongoDbException, "Result is not an CamelMongoDbException");
        CamelMongoDbException camelExc = ObjectHelper.cast(CamelMongoDbException.class, exc);
        if (message != null) {
            assertTrue(camelExc.getMessage().contains(message), "CamelMongoDbException doesn't contain desired message string");
        }
        return camelExc;
    }

}
