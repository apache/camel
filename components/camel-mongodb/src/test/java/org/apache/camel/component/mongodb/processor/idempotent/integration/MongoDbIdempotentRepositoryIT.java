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
package org.apache.camel.component.mongodb.processor.idempotent.integration;

import java.util.UUID;

import org.apache.camel.component.mongodb.integration.AbstractMongoDbITSupport;
import org.apache.camel.component.mongodb.processor.idempotent.MongoDbIdempotentRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbIdempotentRepositoryIT extends AbstractMongoDbITSupport {

    MongoDbIdempotentRepository repo;

    @BeforeEach
    @AfterEach
    public void clearDB() {
        testCollection.deleteMany(new Document());
    }

    @BeforeEach
    public void setupIdempotentRepository() {
        repo = new MongoDbIdempotentRepository(mongo, testCollectionName, dbName);
    }

    @Test
    public void add() {
        String randomUUIDString = UUID.randomUUID().toString();

        boolean added = repo.add(randomUUIDString);
        assertEquals(1, testCollection.countDocuments(), "Driver inserted document");
        assertTrue(added, "Add ui returned true");
    }

    @Test
    public void addAndContains() {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.countDocuments());

        boolean found = repo.contains(randomUUIDString);
        assertTrue(found, "Added uid was found");
    }

    @Test
    public void addAndRemove() {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.countDocuments());

        boolean removed = repo.remove(randomUUIDString);
        assertTrue(removed, "Added uid was removed correctly");
        assertEquals(0, testCollection.countDocuments());
    }

    @Test
    public void addDuplicatedFails() {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.countDocuments());

        boolean added = repo.add(randomUUIDString);
        assertFalse(added, "Duplicated entry was not added");
        assertEquals(1, testCollection.countDocuments());
    }

    @Test
    public void deleteMissingiIsFailse() {
        String randomUUIDString = UUID.randomUUID().toString();
        assertEquals(0, testCollection.countDocuments());
        boolean removed = repo.remove(randomUUIDString);
        assertFalse(removed, "Non exisint uid returns false");
    }

    @Test
    public void containsMissingReturnsFalse() {
        String randomUUIDString = UUID.randomUUID().toString();
        boolean found = repo.contains(randomUUIDString);
        assertFalse(found, "Non existing item is not found");
    }

    @Test
    public void confirmAllwaysReturnsTrue() {
        String randomUUIDString = UUID.randomUUID().toString();
        boolean found = repo.confirm(randomUUIDString);
        assertTrue(found, "Confirm always returns true");

        found = repo.confirm(null);
        assertTrue(found, "Confirm always returns true, even with null");
    }
}
