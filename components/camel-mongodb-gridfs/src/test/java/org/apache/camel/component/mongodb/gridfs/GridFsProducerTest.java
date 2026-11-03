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
package org.apache.camel.component.mongodb.gridfs;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The producer against a mocked bucket: an operation it does not know fails, and listAll lists the ids as text.
 */
class GridFsProducerTest {

    private final GridFSBucket bucket = mock(GridFSBucket.class);
    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = { "findone", "delete", "list" })
    void operationOfTheHeaderThatIsNotSupportedFails(String operation) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(GridFsConstants.GRIDFS_OPERATION, operation);
        exchange.getMessage().setHeader(GridFsConstants.FILE_NAME, "report.csv");

        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> producer(null).process(exchange));
        assertEquals("Unsupported operation: " + operation
                     + ". The supported operations are: create, remove, findOne, listAll and count",
                e.getMessage());
        verifyNoInteractions(bucket);
    }

    @Test
    void operationOfTheOptionThatIsNotSupportedFails() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(GridFsConstants.FILE_NAME, "report.csv");

        assertThrows(IllegalArgumentException.class, () -> producer("findone").process(exchange));
        verifyNoInteractions(bucket);
    }

    @Test
    void listAllGivesTheNameAndTheIdOfEachFile() throws Exception {
        ObjectId id = new ObjectId();
        GridFSFindIterable found = iterableOf(List.of(
                new GridFSFile(new BsonObjectId(id), "report.csv", 1, 1, new Date(), null),
                // a file that another client uploaded with an id of its own
                new GridFSFile(new BsonString("custom-id"), "custom.csv", 1, 1, new Date(), null)));
        when(bucket.find()).thenReturn(found);

        Exchange exchange = new DefaultExchange(context);
        producer("listAll").process(exchange);

        assertEquals("report.csv\t" + id.toHexString() + "\ncustom.csv\tcustom-id\n",
                exchange.getMessage().getBody(String.class));
    }

    private GridFsProducer producer(String operation) {
        GridFsComponent component = new GridFsComponent();
        component.setCamelContext(context);
        GridFsEndpoint endpoint = new GridFsEndpoint("mongodb-gridfs:fake", component) {
            @Override
            public GridFSBucket getGridFsBucket() {
                return bucket;
            }
        };
        endpoint.setCamelContext(context);
        endpoint.setOperation(operation);
        return new GridFsProducer(endpoint);
    }

    @SuppressWarnings("unchecked")
    private static GridFSFindIterable iterableOf(List<GridFSFile> files) {
        Iterator<GridFSFile> iterator = files.iterator();
        MongoCursor<GridFSFile> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next());
        GridFSFindIterable iterable = mock(GridFSFindIterable.class);
        when(iterable.cursor()).thenReturn(cursor);
        return iterable;
    }
}
