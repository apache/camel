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

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The document key is read before the exchange is created, and the resume token only advances after a successful
 * exchange, so anything thrown here made the consumer re-read the same event for as long as the route ran.
 */
public class MongoDbChangeStreamsThreadTest {

    @Test
    public void testGeneratedObjectIdIsReadAsAnObjectId() {
        ObjectId id = new ObjectId();
        BsonDocument key = new BsonDocument("_id", new BsonObjectId(id));

        Object read = MongoDbChangeStreamsThread.readDocumentId(key);

        assertInstanceOf(ObjectId.class, read);
        assertEquals(id, read);
    }

    @Test
    public void testStringIdIsReadAsAString() {
        BsonDocument key = new BsonDocument("_id", new BsonString("a-string-key"));

        assertEquals("a-string-key", MongoDbChangeStreamsThread.readDocumentId(key));
    }

    @Test
    public void testNumericIdIsReadAsANumber() {
        BsonDocument key = new BsonDocument("_id", new BsonInt32(42));

        assertEquals(42, MongoDbChangeStreamsThread.readDocumentId(key));
    }

    @Test
    public void testCompoundIdIsReadAsADocument() {
        BsonDocument compound = new BsonDocument("tenant", new BsonString("acme")).append("seq", new BsonInt32(7));
        BsonDocument key = new BsonDocument("_id", compound);

        Object read = MongoDbChangeStreamsThread.readDocumentId(key);

        assertInstanceOf(Document.class, read);
        assertEquals("acme", ((Document) read).get("tenant"));
        assertEquals(7, ((Document) read).get("seq"));
    }

    @Test
    public void testEventWithoutADocumentKeyHasNoId() {
        // invalidate, drop, rename and dropDatabase carry no document key at all
        assertNull(MongoDbChangeStreamsThread.readDocumentId(null));
    }

    @Test
    public void testDocumentKeyWithoutAnIdHasNoId() {
        assertNull(MongoDbChangeStreamsThread.readDocumentId(new BsonDocument()));
    }
}
