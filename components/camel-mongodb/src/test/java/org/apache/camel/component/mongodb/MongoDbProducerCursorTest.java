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

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every call to MongoIterable.iterator() executes the operation again - it is not an accessor for an already open
 * cursor - so closing "the" cursor in a finally block by calling iterator() a second time ran the whole query twice and
 * left the cursor that had actually been read unclosed.
 */
class MongoDbProducerCursorTest extends CamelTestSupport {

    private final MongoCollection<Document> collection = mock(MongoCollection.class);
    private final DistinctIterable<String> distinctIterable = mock(DistinctIterable.class);
    private final FindIterable<Document> findIterable = mock(FindIterable.class);
    private final AggregateIterable<Document> aggregateIterable = mock(AggregateIterable.class);

    @Test
    void testDistinctOpensOneCursor() {
        // findDistinct needs the field name, otherwise the operation has nothing to ask for
        template.requestBodyAndHeader("direct:distinct", null, MongoDbConstants.DISTINCT_QUERY_FIELD, "name");

        verify(distinctIterable, times(1)).iterator();
    }

    @Test
    void testFindAllOpensOneCursor() {
        template.requestBody("direct:findAll", (Object) null);

        verify(findIterable, times(1)).iterator();
    }

    @Test
    void testAggregateOpensOneCursor() {
        template.requestBody("direct:aggregate", "[{$match: {}}]");

        verify(aggregateIterable, times(1)).iterator();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        when(collection.withWriteConcern(any())).thenReturn(collection);
        when(collection.distinct(anyString(), eq(String.class))).thenReturn(distinctIterable);
        when(collection.distinct(anyString(), any(), eq(String.class))).thenReturn(distinctIterable);
        when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(findIterable);
        when(collection.find()).thenReturn(findIterable);
        when(collection.aggregate(any())).thenReturn(aggregateIterable);

        stubCursor(distinctIterable);
        stubFind(findIterable);
        stubAggregate(aggregateIterable);

        final MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(anyString(), eq(Document.class))).thenReturn(collection);

        final MongoClient client = mock(MongoClient.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(client.listDatabaseNames()).thenReturn(mock(com.mongodb.client.MongoIterable.class));

        context.getRegistry().bind("mongoClient", client);
        return context;
    }

    private void stubCursor(DistinctIterable<String> iterable) {
        final MongoCursor<String> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iterable.iterator()).thenReturn(cursor);
    }

    private void stubFind(FindIterable<Document> iterable) {
        final MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iterable.iterator()).thenReturn(cursor);
        when(iterable.projection(any())).thenReturn(iterable);
        when(iterable.sort(any())).thenReturn(iterable);
        when(iterable.skip(org.mockito.ArgumentMatchers.anyInt())).thenReturn(iterable);
        when(iterable.limit(org.mockito.ArgumentMatchers.anyInt())).thenReturn(iterable);
        when(iterable.allowDiskUse(any())).thenReturn(iterable);
    }

    private void stubAggregate(AggregateIterable<Document> iterable) {
        final MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iterable.iterator()).thenReturn(cursor);
        when(iterable.allowDiskUse(any())).thenReturn(iterable);
        when(iterable.batchSize(org.mockito.ArgumentMatchers.anyInt())).thenReturn(iterable);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:distinct")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=findDistinct&dynamicity=false");
                from("direct:findAll")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=findAll&dynamicity=false");
                from("direct:aggregate")
                        .to("mongodb:mongoClient?database=test&collection=c&operation=aggregate&dynamicity=false");
            }
        };
    }
}
