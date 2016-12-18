/**
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
package org.apache.camel.component.mongodb3.processor.idempotent;

import java.util.UUID;

import com.mongodb.MongoClient;

import org.apache.camel.component.mongodb3.AbstractMongoDbTest;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MongoDbIdempotentRepositoryTest extends AbstractMongoDbTest {

    MongoDbIdempotentRepository<String> repo;

    @Before
    @After
    public void clearDB() {
        testCollection.deleteMany(new Document());
    }

    @Override
    public void doPostSetup() {
        super.doPostSetup();
        repo = new MongoDbIdempotentRepository<>((MongoClient)mongo, testCollectionName, dbName);
    }

    @Test
    public void add() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();

        boolean added = repo.add(randomUUIDString);
        assertEquals("Driver inserted document", 1, testCollection.count());
        assertTrue("Add ui returned true", added);
    }

    @Test
    public void addAndContains() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.count());

        boolean found = repo.contains(randomUUIDString);
        assertTrue("Added uid was found", found);
    }

    @Test
    public void addAndRemove() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.count());

        boolean removed = repo.remove(randomUUIDString);
        assertTrue("Added uid was removed correctly", removed);
        assertEquals(0, testCollection.count());
    }

    @Test
    public void addDuplicatedFails() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();

        repo.add(randomUUIDString);
        assertEquals(1, testCollection.count());

        boolean added = repo.add(randomUUIDString);
        assertTrue("Duplicated entry was not added", !added);
        assertEquals(1, testCollection.count());
    }

    @Test
    public void deleteMissingiIsFailse() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();
        assertEquals(0, testCollection.count());
        boolean removed = repo.remove(randomUUIDString);
        assertTrue("Non exisint uid returns false", !removed);
    }

    @Test
    public void containsMissingReturnsFalse() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();
        boolean found = repo.contains(randomUUIDString);
        assertTrue("Non existing item is not found", !found);
    }

    @Test
    public void confirmAllwaysReturnsTrue() throws Exception {
        String randomUUIDString = UUID.randomUUID().toString();
        boolean found = repo.confirm(randomUUIDString);
        assertTrue("Confirm always returns true", found);

        found = repo.confirm(null);
        assertTrue("Confirm always returns true, even with null", found);
    }

}
