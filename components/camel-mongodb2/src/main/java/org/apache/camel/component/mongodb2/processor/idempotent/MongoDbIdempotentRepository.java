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
package org.apache.camel.component.mongodb2.processor.idempotent;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Mongo db based message id repository")
public class MongoDbIdempotentRepository<E> extends ServiceSupport implements IdempotentRepository<E> {

    private MongoClient mongoClient;
    private String collectionName;
    private String dbName;
    private DBCollection collection;

    public MongoDbIdempotentRepository() {
    }

    public MongoDbIdempotentRepository(MongoClient mongoClient, String collectionName, String dbName) {
        this.mongoClient = mongoClient;
        this.collectionName = collectionName;
        this.dbName = dbName;
        this.collection = mongoClient.getDB(dbName).getCollection(collectionName);
    }

    @ManagedOperation(description = "Adds the key to the store")
    @Override
    public boolean add(E key) {
        BasicDBObject document = new BasicDBObject("_id", key);
        try {
            collection.insert(document);
        } catch (com.mongodb.DuplicateKeyException ex) {
            return false;
        } catch (MongoException ex) {
            throw ex;
        }
        return true;
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @Override
    public boolean contains(E key) {
        BasicDBObject document = new BasicDBObject("_id", key);
        long count =  collection.count(document);
        return count > 0;
    }

    @ManagedOperation(description = "Remove the key from the store")
    @Override
    public boolean remove(E key) {
        BasicDBObject document = new BasicDBObject("_id", key);
        WriteResult res = collection.remove(document);
        return  res.getN() > 0;
    }

    @Override
    public boolean confirm(E key) {
        return true;
    }

    @ManagedOperation(description = "Clear the store")
    @Override
    public void clear() {
        collection.remove(new BasicDBObject());
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(mongoClient, "cli");
        ObjectHelper.notNull(dbName, "dbName");
        ObjectHelper.notNull(collectionName, "collectionName");

        if (collection == null) {
            this.collection = mongoClient.getDB(dbName).getCollection(collectionName);
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

