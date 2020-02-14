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
package org.apache.camel.component.mongodb;

import java.util.Formatter;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractMongoDbTest extends CamelTestSupport {

    protected static final String SCHEME = "mongodb";
    protected static final String USER = "test-user";
    protected static final String PASSWORD = "test-pwd";

    protected static MongoDbContainer container;
    protected static MongoClient mongo;
    protected static MongoDatabase db;
    protected static MongoCollection<Document> testCollection;
    protected static MongoCollection<Document> dynamicCollection;

    protected static String dbName = "test";
    protected static String testCollectionName;
    protected static String dynamicCollectionName;

    @BeforeAll
    public static void doBeforeAll() {
        container = new MongoDbContainer();
        container.start();
    }

    @AfterAll
    public static void doAfterAll() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        mongo = container.createClient();
        db = mongo.getDatabase(dbName);
    }

    @Override
    public void doPostSetup() {
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

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        testCollection.drop();
        dynamicCollection.drop();

        super.tearDown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MongoDbComponent component = new MongoDbComponent();
        component.setMongoConnection(mongo);

        @SuppressWarnings("deprecation")
        CamelContext ctx = new DefaultCamelContext();
        ctx.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");
        ctx.addComponent(SCHEME, component);

        return ctx;
    }

    /**
     * Useful to simulate the presence of an authenticated user with
     * name {@value #USER} and password {@value #PASSWORD}
     */
    protected void createAuthorizationUser() {
        MongoDatabase admin = mongo.getDatabase("admin");
        MongoCollection<Document> usersCollection = admin.getCollection("system.users");
        if (usersCollection.countDocuments() == 0) {
            usersCollection.insertOne(Document.parse("{\n"
                    + "    \"_id\": \"admin.test-user\",\n"
                    + "    \"user\": \"test-user\",\n"
                    + "    \"db\": \"admin\",\n"
                    + "    \"credentials\": {\n"
                    + "        \"SCRAM-SHA-1\": {\n"
                    + "            \"iterationCount\": 10000,\n"
                    + "            \"salt\": \"gmmPAoNdvFSWCV6PGnNcAw==\",\n"
                    + "            \"storedKey\": \"qE9u1Ax7Y40hisNHL2b8/LAvG7s=\",\n"
                    + "            \"serverKey\": \"RefeJcxClt9JbOP/VnrQ7YeQh6w=\"\n"
                    + "        }\n" + "    },\n"
                    + "    \"roles\": [\n" + "        {\n"
                    + "            \"role\": \"readWrite\",\n"
                    + "            \"db\": \"test\"\n"
                    + "        }\n"
                    + "    ]\n"
                    + "}"));
        }
    }

    protected void pumpDataIntoTestCollection() {
        // there should be 100 of each
        String[] scientists = {"Einstein", "Darwin", "Copernicus", "Pasteur", "Curie", "Faraday", "Newton", "Bohr", "Galilei", "Maxwell"};
        for (int i = 1; i <= 1000; i++) {
            int index = i % scientists.length;
            Formatter f = new Formatter();
            String doc = f.format("{\"_id\":\"%d\", \"scientist\":\"%s\", \"fixedField\": \"fixedValue\"}", i, scientists[index]).toString();
            IOHelper.close(f);
            testCollection.insertOne(Document.parse(doc));
        }
        assertEquals(1000L, testCollection.countDocuments(), "Data pumping of 1000 entries did not complete entirely");
    }

    protected CamelMongoDbException extractAndAssertCamelMongoDbException(Object result, String message) {
        assertTrue(result instanceof Throwable, "Result is not an Exception");
        assertTrue(result instanceof CamelExecutionException, "Result is not an CamelExecutionException");
        Throwable exc = ((CamelExecutionException)result).getCause();
        assertTrue(exc instanceof CamelMongoDbException, "Result is not an CamelMongoDbException");
        CamelMongoDbException camelExc = ObjectHelper.cast(CamelMongoDbException.class, exc);
        if (message != null) {
            assertTrue(camelExc.getMessage().contains(message), "CamelMongoDbException doesn't contain desired message string");
        }
        return camelExc;
    }

}
