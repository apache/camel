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

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.Document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mocks of the MongoDB driver shared by the tests of the change streams consumer: the database {@code test} with the
 * collection {@code events}, a change stream that keeps the resume options it is given and opens its cursor through the
 * test, a cursor over a list of events, a change event, and the error of a command.
 */
final class MongoDbChangeStreamsMocks {

    static final ServerAddress SERVER = new ServerAddress("localhost", 27017);

    private MongoDbChangeStreamsMocks() {
    }

    /** The resume options a change stream was opened with (none, one, or more when a test sets them). */
    static final class ResumeOptions {
        BsonDocument resumeAfter;
        BsonDocument startAfter;
        BsonTimestamp startAtOperationTime;
    }

    @SuppressWarnings("unchecked")
    static MongoCollection<Document> mockCollection() {
        return mock(MongoCollection.class);
    }

    /**
     * Mocks the database {@code test} of the client with the given collection as {@code events}, and makes every
     * {@code collection.watch()} a new change stream whose cursor is opened by {@code open}, as the aggregate command
     * does when the consumer calls {@code iterator()}.
     */
    static MongoDatabase mockDatabase(
            MongoClient client, MongoCollection<Document> collection,
            Function<ResumeOptions, MongoChangeStreamCursor<ChangeStreamDocument<Document>>> open) {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable names = mock(ListCollectionNamesIterable.class);
        when(names.spliterator()).thenAnswer(i -> List.of("events").spliterator());
        when(database.listCollectionNames()).thenReturn(names);
        when(database.getCollection("events", Document.class)).thenReturn(collection);
        when(database.getName()).thenReturn("test");
        when(client.getDatabase("test")).thenReturn(database);
        when(collection.watch()).thenAnswer(i -> changeStream(open));
        return database;
    }

    @SuppressWarnings("unchecked")
    private static ChangeStreamIterable<Document> changeStream(
            Function<ResumeOptions, MongoChangeStreamCursor<ChangeStreamDocument<Document>>> open) {
        ChangeStreamIterable<Document> iterable = mock(ChangeStreamIterable.class);
        ResumeOptions options = new ResumeOptions();
        when(iterable.fullDocument(any(FullDocument.class))).thenReturn(iterable);
        when(iterable.resumeAfter(any(BsonDocument.class))).thenAnswer(i -> {
            options.resumeAfter = i.getArgument(0);
            return iterable;
        });
        when(iterable.startAfter(any(BsonDocument.class))).thenAnswer(i -> {
            options.startAfter = i.getArgument(0);
            return iterable;
        });
        when(iterable.startAtOperationTime(any(BsonTimestamp.class))).thenAnswer(i -> {
            options.startAtOperationTime = i.getArgument(0);
            return iterable;
        });
        when(iterable.iterator()).thenAnswer(i -> open.apply(options));
        return iterable;
    }

    /**
     * A cursor over the given events whose server cursor is closed after the last one: {@code tryNext()} then returns
     * null and {@code getServerCursor()} null, as for a cursor that the server closed ({@code hasNext()} returns
     * false).
     */
    @SuppressWarnings("unchecked")
    static MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor(List<ChangeStreamDocument<Document>> events) {
        Iterator<ChangeStreamDocument<Document>> it = events.iterator();
        MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = mock(MongoChangeStreamCursor.class);
        when(cursor.hasNext()).thenAnswer(i -> it.hasNext());
        when(cursor.next()).thenAnswer(i -> it.next());
        when(cursor.tryNext()).thenAnswer(i -> it.hasNext() ? it.next() : null);
        return cursor;
    }

    /** A change event; its document key is the {@code _id} of the full document, when there is one. */
    @SuppressWarnings("unchecked")
    static ChangeStreamDocument<Document> event(OperationType type, BsonDocument token, Document fullDocument) {
        ChangeStreamDocument<Document> event = mock(ChangeStreamDocument.class);
        when(event.getOperationType()).thenReturn(type);
        when(event.getResumeToken()).thenReturn(token);
        when(event.getFullDocument()).thenReturn(fullDocument);
        if (fullDocument != null) {
            when(event.getDocumentKey()).thenReturn(new BsonDocument("_id", new BsonInt32(fullDocument.getInteger("_id"))));
        }
        return event;
    }

    /** The reply of a command that failed with the given server error. */
    static BsonDocument errorReply(int code, String codeName, String message) {
        return new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code))
                .append("codeName", new BsonString(codeName)).append("errmsg", new BsonString(message));
    }

    /** The exception the driver throws for a command that failed with the given server error. */
    static MongoCommandException commandFailed(int code, String codeName, String message) {
        return new MongoCommandException(errorReply(code, codeName, message), SERVER);
    }
}
