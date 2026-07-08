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

import org.apache.camel.spi.StateRepository;
import org.apache.camel.util.ObjectHelper;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeStreamCommitManager implements CommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(ChangeStreamCommitManager.class);

    private final StateRepository<String, String> resumeTokenRepository;
    private final String resumeTokenKey;
    private final String explicitResumeToken;

    private BsonDocument cachedResumeToken;

    public ChangeStreamCommitManager(MongoDbChangeStreamsConsumer consumer, MongoDbEndpoint endpoint) {
        this.resumeTokenRepository = endpoint.getChangeStreamTokenRepository();
        this.explicitResumeToken = endpoint.getChangeStreamToken();

        String routeId = consumer.getRouteId();
        if (ObjectHelper.isEmpty(routeId)) {
            routeId = endpoint.getEndpointUri();
        }

        this.resumeTokenKey = serializeResumeTokenKey(routeId, endpoint.getCollection());
    }

    @Override
    public BsonDocument readResumeToken() {
        if (ObjectHelper.isNotEmpty(explicitResumeToken)) {
            return BsonDocument.parse(explicitResumeToken);
        }

        if (resumeTokenRepository == null) {
            return null;
        }

        String serialized = resumeTokenRepository.getState(resumeTokenKey);
        if (ObjectHelper.isEmpty(serialized)) {
            return null;
        }

        return BsonDocument.parse(serialized);
    }

    @Override
    public void recordResumeToken(BsonDocument resumeToken) {
        this.cachedResumeToken = resumeToken;
    }

    @Override
    public void commit() throws Exception {
        if (cachedResumeToken == null) {
            return;
        }

        if (resumeTokenRepository != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Saving resume token repository state for key {}", resumeTokenKey);
            }
            resumeTokenRepository.setState(resumeTokenKey, cachedResumeToken.toJson());
        }
    }

    private static String serializeResumeTokenKey(String routeId, String collection) {
        return routeId + '/' + collection;
    }
}
