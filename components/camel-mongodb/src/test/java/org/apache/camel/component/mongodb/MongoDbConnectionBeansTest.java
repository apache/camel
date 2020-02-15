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
import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MongoDbConnectionBeansTest extends AbstractMongoDbTest {
    @Test
    public void checkConnectionFromProperties() {
        MongoClient client = container.createClient();

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", client);

        MongoDbEndpoint testEndpoint = context.getEndpoint("mongodb:anyName?mongoConnection=#myDb", MongoDbEndpoint.class);

        assertNotEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(client, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkConnectionFromBean() {
        MongoClient client = container.createClient();

        context.getComponent(SCHEME, MongoDbComponent.class).setMongoConnection(null);
        context.getRegistry().bind("myDb", client);

        MongoDbEndpoint testEndpoint = context.getEndpoint("mongodb:myDb", MongoDbEndpoint.class);
        assertEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(client, testEndpoint.getMongoConnection());
    }


    @Test
    public void checkConnectionBothExisting() {
        MongoClient client1 = container.createClient();
        MongoClient client2 = container.createClient();

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

    @Test
    public void checkConnectionOnComponent() throws Exception {
        Endpoint endpoint = context.getEndpoint("mongodb:justARouteName");

        assertIsInstanceOf(MongoDbEndpoint.class, endpoint);
        assertEquals(mongo, ((MongoDbEndpoint) endpoint).getMongoConnection());
    }
}
