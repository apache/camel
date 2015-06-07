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
package org.apache.camel.component.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteConcern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDbTailTrackingManager {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbTailTrackingManager.class);
    
    public Object lastVal;

    private final Mongo connection;
    private final MongoDbTailTrackingConfig config;
    private DBCollection dbCol;
    private DBObject trackingObj;
    
    public MongoDbTailTrackingManager(Mongo connection, MongoDbTailTrackingConfig config) {
        this.connection = connection;
        this.config = config;
    }
    
    public void initialize() throws Exception {
        if (!config.persistent) {
            return;
        }
        
        dbCol = connection.getDB(config.db).getCollection(config.collection);
        DBObject filter = new BasicDBObject("persistentId", config.persistentId);
        trackingObj = dbCol.findOne(filter);
        if (trackingObj == null) {
            dbCol.insert(filter, WriteConcern.SAFE);
            trackingObj = dbCol.findOne(filter);
        }
        // keep only the _id, the rest is useless and causes more overhead during update
        trackingObj = new BasicDBObject("_id", trackingObj.get("_id"));
    }
    
    public synchronized void persistToStore() {
        if (!config.persistent || lastVal == null) {
            return;
        }
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Persisting lastVal={} to store, collection: {}", lastVal, config.collection);
        }
        
        DBObject updateObj = BasicDBObjectBuilder.start().add("$set", new BasicDBObject(config.field, lastVal)).get();
        dbCol.update(trackingObj, updateObj, false, false, WriteConcern.SAFE);
        trackingObj = dbCol.findOne();
    }
    
    public synchronized Object recoverFromStore() {
        if (!config.persistent) {
            return null;
        }
        
        lastVal = dbCol.findOne(trackingObj).get(config.field);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Recovered lastVal={} from store, collection: {}", lastVal, config.collection);
        }
        
        return lastVal;
    }
    
    public void setLastVal(DBObject o) {
        if (config.increasingField == null) {
            return;
        }
        
        lastVal = o.get(config.increasingField);
    }
    
    public String getIncreasingFieldName() {
        return config.increasingField;
    }
}
