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
package org.apache.camel.component.mongodb.integration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.camel.component.mongodb.MongoDbComponent;
import org.apache.camel.component.mongodb.MongoDbEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MongoDbConnectionBeansIT extends AbstractMongoDbITSupport {
    @Test
    public void checkConnectionFromProperties() {
        MongoClient client = MongoClients.create(service.getReplicaSetUrl());

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", client);

        MongoDbEndpoint testEndpoint = context.getEndpoint("mongodb:anyName?mongoConnection=#myDb", MongoDbEndpoint.class);

        assertNotEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(client, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkConnectionFromBean() {
        MongoClient client = MongoClients.create(service.getReplicaSetUrl());

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", client);

        MongoDbEndpoint testEndpoint = context.getEndpoint("mongodb:myDb", MongoDbEndpoint.class);
        assertEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(client, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkConnectionBothExisting() {
        MongoClient client1 = MongoClients.create(service.getReplicaSetUrl());
        MongoClient client2 = MongoClients.create(service.getReplicaSetUrl());

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", client1);
        context.getRegistry().bind("myDbS", client2);

        MongoDbEndpoint testEndpoint = context.getEndpoint("mongodb:myDb?mongoConnection=#myDbS", MongoDbEndpoint.class);
        MongoClient myDbS = context.getRegistry().lookupByNameAndType("myDbS", MongoClient.class);

        assertEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(myDbS, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkMissingConnection() {
        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        assertThrows(Exception.class, () -> context.getEndpoint("mongodb:anythingNotRelated", MongoDbEndpoint.class));
    }
}
