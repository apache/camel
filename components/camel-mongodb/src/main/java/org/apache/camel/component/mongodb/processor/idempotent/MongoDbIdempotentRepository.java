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

package org.apache.camel.component.mongodb.processor.idempotent;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

import com.mongodb.ErrorCategory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;

@Metadata(
        label = "bean",
        description = "Idempotent repository that uses MongoDB to store message ids.",
        annotations = {"interfaceName=org.apache.camel.spi.IdempotentRepository"})
@Configurer(metadataOnly = true)
@ManagedResource(description = "MongoDB based message id repository")
public class MongoDbIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    @Metadata(description = "The MongoClient to use for connecting to the MongoDB server", required = true)
    private MongoClient mongoClient;

    @Metadata(description = "The Database name", required = true)
    private String dbName;

    @Metadata(description = "The collection name", required = true)
    private String collectionName;

    private MongoCollection<Document> collection;

    public MongoDbIdempotentRepository() {}

    public MongoDbIdempotentRepository(MongoClient mongoClient, String collectionName, String dbName) {
        this.mongoClient = mongoClient;
        this.collectionName = collectionName;
        this.dbName = dbName;
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(String key) {
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
    public boolean contains(String key) {
        Bson document = eq(MONGO_ID, key);
        long count = collection.countDocuments(document);
        return count > 0;
    }

    @ManagedOperation(description = "Remove the key from the store")
    @Override
    public boolean remove(String key) {
        Bson document = eq(MONGO_ID, key);
        DeleteResult res = collection.deleteOne(document);
        return res.getDeletedCount() > 0;
    }

    @Override
    public boolean confirm(String key) {
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
        // noop
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
