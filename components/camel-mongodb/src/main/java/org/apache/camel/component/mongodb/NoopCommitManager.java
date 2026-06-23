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

import org.apache.camel.util.ObjectHelper;
import org.bson.BsonDocument;

public class NoopCommitManager implements CommitManager {

    private final MongoDbEndpoint endpoint;

    public NoopCommitManager(MongoDbEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public BsonDocument readResumeToken() {
        if (ObjectHelper.isEmpty(endpoint.getChangeStreamToken())) {
            return null;
        }

        return BsonDocument.parse(endpoint.getChangeStreamToken());
    }

    @Override
    public void recordResumeToken(BsonDocument resumeToken) {
        // NO-OP
    }

    @Override
    public void commit() {
        // NO-OP
    }
}
