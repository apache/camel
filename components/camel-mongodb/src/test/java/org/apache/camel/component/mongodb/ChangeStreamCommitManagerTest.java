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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChangeStreamCommitManagerTest {

    private static MongoDbEndpoint createEndpoint() {
        CamelContext context = new DefaultCamelContext();
        MongoDbComponent component = new MongoDbComponent(context);
        MongoDbEndpoint endpoint = new MongoDbEndpoint("mongodb:myDb", component);
        endpoint.setCollection("camelTest");
        return endpoint;
    }

    @Test
    public void shouldReadExplicitResumeTokenBeforeRepositoryToken() {
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.setState("mongodb:myDb/camelTest", "{\"_data\":\"repo-token\"}");

        MongoDbEndpoint endpoint = createEndpoint();
        endpoint.setChangeStreamTokenRepository(repository);
        endpoint.setChangeStreamToken("{\"_data\":\"explicit-token\"}");

        MongoDbChangeStreamsConsumer consumer = new MongoDbChangeStreamsConsumer(endpoint, e -> {
        });
        ChangeStreamCommitManager manager = new ChangeStreamCommitManager(consumer, endpoint);

        BsonDocument token = manager.readResumeToken();
        assertNotNull(token);
        assertEquals("explicit-token", token.getString("_data").getValue());
    }

    @Test
    public void shouldCommitSerializedTokenToRepository() throws Exception {
        MemoryStateRepository repository = new MemoryStateRepository();

        MongoDbEndpoint endpoint = createEndpoint();
        endpoint.setChangeStreamTokenRepository(repository);

        MongoDbChangeStreamsConsumer consumer = new MongoDbChangeStreamsConsumer(endpoint, e -> {
        });

        ChangeStreamCommitManager manager = new ChangeStreamCommitManager(consumer, endpoint);
        BsonDocument token = BsonDocument.parse("{\"_data\":\"new-token\"}");

        manager.recordResumeToken(token);
        manager.commit();

        assertEquals(token.toJson(), repository.getState("mongodb:myDb/camelTest"));
    }
}
