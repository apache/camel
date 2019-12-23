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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mongodb.MongoDbConstants.MONGO_ID;

public class MongoDbTailTrackingManager {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbTailTrackingManager.class);

    public Object lastVal;

    private final MongoClient connection;
    private final MongoDbTailTrackingConfig config;
    private MongoCollection<Document> dbCol;
    private Document trackingObj;

    public MongoDbTailTrackingManager(MongoClient connection, MongoDbTailTrackingConfig config) {
        this.connection = connection;
        this.config = config;
    }

    public void initialize() {
        if (!config.persistent) {
            return;
        }

        dbCol = connection.getDatabase(config.db).getCollection(config.collection, Document.class);
        Document filter = new Document("persistentId", config.persistentId);
        trackingObj = dbCol.find(filter).first();
        if (trackingObj == null) {
            dbCol.insertOne(filter);
            trackingObj = dbCol.find(filter).first();
        }
        // keep only the _id, the rest is useless and causes more overhead during update
        trackingObj = new Document(MONGO_ID, trackingObj.get(MONGO_ID));
    }

    public synchronized void persistToStore() {
        if (!config.persistent || lastVal == null) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Persisting lastVal={} to store, collection: {}", lastVal, config.collection);
        }

        Bson updateObj = Updates.set(config.field, lastVal);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        trackingObj = dbCol.findOneAndUpdate(trackingObj, updateObj, options);
    }

    public synchronized Object recoverFromStore() {
        if (!config.persistent) {
            return null;
        }

        lastVal = dbCol.find(trackingObj).first().get(config.field);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Recovered lastVal={} from store, collection: {}", lastVal, config.collection);
        }

        return lastVal;
    }

    public void setLastVal(Document dbObj) {
        if (config.increasingField == null) {
            return;
        }

        lastVal = dbObj.get(config.increasingField);
    }

    public String getIncreasingFieldName() {
        return config.increasingField;
    }
}
