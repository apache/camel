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
import org.apache.camel.component.mongodb.support.InMemoryStringResumeCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.resume.mongodb.MongoDbResumeStrategy;
import org.apache.camel.processor.resume.mongodb.MongoDbResumeStrategyConfigurationBuilder;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.apache.camel.support.resume.ResumeStrategyHelper;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MongoDbResumeAdapterTest {

    private static MongoDbEndpoint createEndpoint(CamelContext context) {
        MongoDbComponent component = new MongoDbComponent(context);
        MongoDbEndpoint endpoint = new MongoDbEndpoint("mongodb:myDb", component);
        endpoint.setCollection("camelTest");
        return endpoint;
    }

    @Test
    public void shouldSetStartupTokenFromStrategyCacheLoad() throws Exception {
        CamelContext context = new DefaultCamelContext();
        MongoDbEndpoint endpoint = createEndpoint(context);
        MongoDbChangeStreamsConsumer consumer = new MongoDbChangeStreamsConsumer(endpoint, e -> {
        });

        String key = endpoint.getEndpointUri() + "/camelTest";
        String expected = "{\"_data\":\"strategy-token\"}";
        InMemoryStringResumeCache resumeCache = new InMemoryStringResumeCache();

        MemoryStateRepository repository = new MemoryStateRepository();
        repository.setState(key, expected);

        MongoDbResumeStrategy strategy = new MongoDbResumeStrategy();
        strategy.setResumeStrategyConfiguration(MongoDbResumeStrategyConfigurationBuilder.newBuilder()
                .withStateRepository(repository)
                .withResumeCache(resumeCache)
                .build());

        MongoDbResumeAdapter adapter = new MongoDbResumeAdapter();
        adapter.setCache(resumeCache);
        adapter.setConsumer(consumer);
        adapter.setResumeTokenKey(key);

        strategy.setAdapter(adapter);

        ResumeStrategyHelper.resume(context, consumer, strategy, "mongodb-resume", ResumeAdapter.class);

        BsonDocument startupToken = consumer.getStartupResumeToken();
        assertNotNull(startupToken);
        assertEquals("strategy-token", startupToken.getString("_data").getValue());
    }
}
