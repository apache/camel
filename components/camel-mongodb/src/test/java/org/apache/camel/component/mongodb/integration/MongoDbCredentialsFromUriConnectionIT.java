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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test class performs the same tests as DBOperationsIT but with modified URIs
public class MongoDbCredentialsFromUriConnectionIT extends MongoDbOperationsIT {

    protected static final String AUTH_SOURCE_USER = "auth-source-user";
    protected static final String AUTH_SOURCE_PASSWORD = "auth-source-password";

    @Override
    public void doPreSetup() throws Exception {
        // create user in db
        super.doPreSetup();
        createAuthorizationUser();
        createAuthorizationUser(dbName, AUTH_SOURCE_USER, AUTH_SOURCE_PASSWORD);
    }

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testCountOperationAuthUser() {
        Object result = template.requestBody("direct:testAuthSource", "irrelevantBody");
        assertTrue(result instanceof Long, "Result is not of type Long");
        assertEquals(0L, result, "Test collection should not contain any records");

        // Insert a record and test that the endpoint now returns 1
        testCollection.insertOne(Document.parse("{a:60}"));
        result = template.requestBody("direct:testAuthSource", "irrelevantBody");
        assertTrue(result instanceof Long, "Result is not of type Long");
        assertEquals(1L, result, "Test collection should contain 1 record");
        testCollection.deleteOne(new Document());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                String uriHostnameOnly = String.format("mongodb:mongo?hosts=%s&", service.getConnectionAddress());
                //connecting with credentials for created user
                String uriWithCredentials = String.format("%susername=%s&password=%s&", uriHostnameOnly, USER, PASSWORD);

                String uriWithAuthSource = String.format(
                        "%susername=%s&password=%s&authSource=%s&",
                        uriHostnameOnly, AUTH_SOURCE_USER, AUTH_SOURCE_PASSWORD, dbName);

                from("direct:count").to(
                        uriHostnameOnly + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:insert")
                        .to(uriWithCredentials
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
                from("direct:testStoreOidOnInsert")
                        .to(uriHostnameOnly
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
                        .setBody()
                        .header(MongoDbConstants.OID);
                from("direct:save")
                        .to(uriWithCredentials
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save");
                from("direct:testStoreOidOnSave")
                        .to(uriWithCredentials
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save")
                        .setBody()
                        .header(MongoDbConstants.OID);
                from("direct:update")
                        .to(uriWithCredentials
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=update");
                from("direct:remove")
                        .to(uriWithCredentials
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=remove");
                from("direct:aggregate").to(
                        uriHostnameOnly + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate");
                from("direct:getDbStats").to(uriWithCredentials + "database={{mongodb.testDb}}&operation=getDbStats");
                from("direct:getColStats").to(
                        uriWithCredentials + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getColStats");
                from("direct:command").to(uriWithCredentials + "database={{mongodb.testDb}}&operation=command");
                from("direct:testAuthSource")
                        .to(uriWithAuthSource
                            + "database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
            }
        };
    }
}
