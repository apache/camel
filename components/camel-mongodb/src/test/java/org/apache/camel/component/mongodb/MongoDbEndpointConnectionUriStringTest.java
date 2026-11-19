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

import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Which client a mongodb endpoint uses when it has a connectionUriString, a client in the registry, or both.
 */
class MongoDbEndpointConnectionUriStringTest extends CamelTestSupport {

    private static final String URI_ENDPOINT
            = "mongodb:dummy?connectionUriString=RAW(mongodb://localhost:27017/?readPreference=secondaryPreferred)"
              + "&database=test&collection=test&operation=findAll";

    @Test
    void connectionUriStringIsUsedInsteadOfTheClientOfTheComponent() {
        MongoClient registryClient = mock(MongoClient.class);
        context.getRegistry().bind("registryClient", registryClient);

        MongoDbEndpoint endpoint = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class);

        MongoClient client = endpoint.getMongoConnection();
        assertNotSame(registryClient, client);
        assertEquals(ReadPreference.secondaryPreferred(), client.getReadPreference());
    }

    @Test
    void connectionUriStringIsUsedWhenNoClientIsInTheRegistry() {
        MongoDbEndpoint endpoint = context.getEndpoint(URI_ENDPOINT, MongoDbEndpoint.class);

        MongoClient client = endpoint.getMongoConnection();
        assertNotNull(client);
        assertEquals(ReadPreference.secondaryPreferred(), client.getReadPreference());
    }

    @Test
    void connectionBeanIsUsedWithoutConnectionUriStringAndHosts() {
        MongoClient firstClient = mock(MongoClient.class);
        MongoClient secondClient = mock(MongoClient.class);
        context.getRegistry().bind("firstClient", firstClient);
        context.getRegistry().bind("secondClient", secondClient);

        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:secondClient?database=test&collection=test&operation=findAll", MongoDbEndpoint.class);

        assertSame(secondClient, endpoint.getMongoConnection());
    }

    @Test
    void clientOfTheComponentIsUsedWithoutConnectionUriStringAndHosts() {
        MongoClient registryClient = mock(MongoClient.class);
        context.getRegistry().bind("registryClient", registryClient);

        MongoDbEndpoint endpoint = context.getEndpoint(
                "mongodb:dummy?database=test&collection=test&operation=findAll", MongoDbEndpoint.class);

        assertSame(registryClient, endpoint.getMongoConnection());
    }

    @Test
    void mongoConnectionOptionIsUsedBeforeConnectionUriString() {
        MongoClient explicitClient = mock(MongoClient.class);
        MongoClient otherClient = mock(MongoClient.class);
        context.getRegistry().bind("explicitClient", explicitClient);
        context.getRegistry().bind("otherClient", otherClient);

        MongoDbEndpoint endpoint
                = context.getEndpoint(URI_ENDPOINT + "&mongoConnection=#explicitClient", MongoDbEndpoint.class);

        assertSame(explicitClient, endpoint.getMongoConnection());
    }
}
