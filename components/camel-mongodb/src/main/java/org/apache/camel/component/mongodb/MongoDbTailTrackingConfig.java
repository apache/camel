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

public class MongoDbTailTrackingConfig {
    
    static final String DEFAULT_COLLECTION = "camelTailTracking";
    static final String DEFAULT_FIELD = "lastTrackingValue";

    /**
     * See {@link MongoDbEndpoint#setTailTrackDb(String)}
     */
    public final String db;
    /**
     * See {@link MongoDbEndpoint#setTailTrackCollection(String)}
     */
    public final String collection;
    /**
     * See {@link MongoDbEndpoint#setTailTrackIncreasingField(String)}
     */
    final String increasingField;
    /**
     * See {@link MongoDbEndpoint#setPersistentTailTracking(boolean)}
     */
    final boolean persistent;
    /**
     * See {@link MongoDbEndpoint#setTailTrackField(String)}
     */
    final String field;
    /**
     * See {@link MongoDbEndpoint#setPersistentId(String)}
     */
    final String persistentId;

    /**
     * See {@link MongoDbEndpoint#setTailTrackingStrategy(MongoDBTailTrackingEnum)}
     */
    final MongoDBTailTrackingEnum mongoDBTailTrackingStrategy;
    
    public MongoDbTailTrackingConfig(boolean persistentTailTracking, String tailTrackIncreasingField, String tailTrackDb,
            String tailTrackCollection, String tailTrackField, String persistentId, MongoDBTailTrackingEnum mongoDBTailTrackingStrategy) {
        this.increasingField = tailTrackIncreasingField;
        this.persistent = persistentTailTracking;
        this.db = tailTrackDb;
        this.persistentId = persistentId;
        this.collection = tailTrackCollection == null ? MongoDbTailTrackingConfig.DEFAULT_COLLECTION : tailTrackCollection;
        this.field = tailTrackField == null ? MongoDbTailTrackingConfig.DEFAULT_FIELD : tailTrackField;
        this.mongoDBTailTrackingStrategy = mongoDBTailTrackingStrategy == null ? MongoDBTailTrackingEnum.LITERAL : mongoDBTailTrackingStrategy;
    }
}