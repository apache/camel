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
package org.apache.camel.processor.resume.mongodb;

import org.apache.camel.component.mongodb.MongoDbResumable;
import org.apache.camel.component.mongodb.support.InMemoryStringResumeCache;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MongoDbResumeStrategyTest {

    @Test
    public void shouldStoreAndLoadOffsetsUsingStateRepository() throws Exception {
        MemoryStateRepository repository = new MemoryStateRepository();

        MongoDbResumeStrategyConfiguration configuration = MongoDbResumeStrategyConfigurationBuilder.newBuilder()
                .withStateRepository(repository)
                .build();
        MongoDbResumeStrategy strategy = new MongoDbResumeStrategy();
        strategy.setResumeStrategyConfiguration(configuration);

        String key = "routeA/testCollection";
        BsonDocument token = BsonDocument.parse("{\"_data\":\"token-1\"}");

        strategy.updateLastOffset(MongoDbResumable.of(key, token));

        assertEquals(token.toJson(), repository.getState(key));
        assertEquals(token.toJson(), strategy.getLastOffset(key));
    }

    @Test
    public void shouldStoreAndLoadOffsetsUsingResumeCache() throws Exception {
        InMemoryStringResumeCache resumeCache = new InMemoryStringResumeCache();

        MongoDbResumeStrategyConfiguration configuration = MongoDbResumeStrategyConfigurationBuilder.newBuilder()
                .withResumeCache(resumeCache)
                .withCacheFillPolicy(Cacheable.FillPolicy.MAXIMIZING)
                .build();

        MongoDbResumeStrategy strategy = new MongoDbResumeStrategy();
        strategy.setResumeStrategyConfiguration(configuration);

        String key = "routeB/testCollection";
        BsonDocument token = BsonDocument.parse("{\"_data\":\"token-2\"}");

        strategy.updateLastOffset(MongoDbResumable.of(key, token));

        assertEquals(token.toJson(), resumeCache.get(key, String.class));
        assertEquals(token.toJson(), strategy.getLastOffset(key));
        assertNull(strategy.getLastOffset("missing"));
    }

}
