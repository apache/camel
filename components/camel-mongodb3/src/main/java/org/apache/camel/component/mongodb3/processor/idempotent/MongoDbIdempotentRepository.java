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

import com.mongodb.ErrorCategory;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

@ManagedResource(description = "Mongo db based message id repository")
public class MongoDbIdempotentRepository<E> extends ServiceSupport implements IdempotentRepository<E> {
    private MongoClient mongoClient;
    private String collectionName;
    private String dbName;
    private MongoCollection<Document> collection;

    public MongoDbIdempotentRepository() {
    }

    public MongoDbIdempotentRepository(MongoClient mongoClient, String collectionName, String dbName) {
        this.mongoClient = mongoClient;
        this.collectionName = collectionName;
        this.dbName = dbName;
        this.collection = mongoClient.getDatabase(dbName).getCollection(collectionName);
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(E key) {
        Document document = new Document(MONGO_ID, key);
        try {
            collection.insertOne(document);
        } catch (com.mongodb.MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw ex;
        }
        return true;
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @Override
    public boolean contains(E key) {
        Bson document = eq(MONGO_ID, key);
        long count = collection.count(document);
        return count > 0;
    }

    @ManagedOperation(description = "Remove the key from the store")
    @Override
    public boolean remove(E key) {
        Bson document = eq(MONGO_ID, key);
        DeleteResult res = collection.deleteOne(document);
        return res.getDeletedCount() > 0;
    }

    @Override
    public boolean confirm(E key) {
        return true;
    }

    @ManagedOperation(description = "Clear the store")
    @Override
    public void clear() {
        collection.deleteMany(new Document());
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(mongoClient, "cli");
        ObjectHelper.notNull(dbName, "dbName");
        ObjectHelper.notNull(collectionName, "collectionName");

        if (collection == null) {
            this.collection = mongoClient.getDatabase(dbName).getCollection(collectionName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        return;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public void setMongoClient(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
}
