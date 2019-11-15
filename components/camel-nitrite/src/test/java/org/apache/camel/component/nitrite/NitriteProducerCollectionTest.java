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
package org.apache.camel.component.nitrite;

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.operation.collection.FindCollectionOperation;
import org.apache.camel.component.nitrite.operation.collection.RemoveCollectionOperation;
import org.apache.camel.component.nitrite.operation.collection.UpdateCollectionOperation;
import org.apache.camel.component.nitrite.operation.common.CreateIndexOperation;
import org.apache.camel.component.nitrite.operation.common.DropIndexOperation;
import org.apache.camel.component.nitrite.operation.common.ExportDatabaseOperation;
import org.apache.camel.component.nitrite.operation.common.GetAttributesOperation;
import org.apache.camel.component.nitrite.operation.common.GetByIdOperation;
import org.apache.camel.component.nitrite.operation.common.ImportDatabaseOperation;
import org.apache.camel.component.nitrite.operation.common.InsertOperation;
import org.apache.camel.component.nitrite.operation.common.ListIndicesOperation;
import org.apache.camel.component.nitrite.operation.common.RebuildIndexOperation;
import org.apache.camel.component.nitrite.operation.common.UpdateOperation;
import org.apache.camel.component.nitrite.operation.common.UpsertOperation;
import org.apache.camel.component.nitrite.operation.repository.FindRepositoryOperation;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.FileUtil;
import org.dizitart.no2.Document;
import org.dizitart.no2.IndexOptions;
import org.dizitart.no2.IndexType;
import org.dizitart.no2.NitriteId;
import org.dizitart.no2.WriteResult;
import org.dizitart.no2.filters.Filters;
import org.dizitart.no2.meta.Attributes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NitriteProducerCollectionTest extends AbstractNitriteTest {

    @Before
    public void insertData() {
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1-a").put("key2", "value2-a").put("key3", "value3-a").put("_id", 1L)
        );
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1-b").put("key2", "value2-b").put("key3", "value3-b").put("_id", 2L)
        );
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1-c").put("key2", "value2-c").put("key3", "value3-c").put("_id", 3L)
        );
    }

    @Test
    public void findCollectionOperation() throws Exception {
        List<Document> result = template.requestBodyAndHeader(
                String.format("nitrite://%s?collection=collection", tempDb()),
                null,
                NitriteConstants.OPERATION, new FindCollectionOperation(Filters.eq("key1", "value1-a")),
                List.class
        );

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("value1-a", result.get(0).get("key1"));
        Assert.assertEquals("value2-a", result.get(0).get("key2"));
        Assert.assertEquals("value3-a", result.get(0).get("key3"));
    }

    @Test
    public void removeCollectionOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new RemoveCollectionOperation(Filters.eq("key2", "value2-b")));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void updateCollectionOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpdateCollectionOperation(Filters.eq("key2", "value2-b")));
        exchange.getMessage().setBody(Document.createDocument("key3", "updatedValue"));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void createIndexOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new CreateIndexOperation("key3", IndexOptions.indexOptions(IndexType.Unique)));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);

        Exchange listIndices = new DefaultExchange(context);
        listIndices.getMessage().setHeader(NitriteConstants.OPERATION, new ListIndicesOperation());
        template.send(String.format("nitrite://%s?collection=collection", tempDb()), listIndices);

        Assert.assertEquals(1, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void dropIndexOperation() throws Exception {
        createIndexOperation();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new DropIndexOperation("key3"));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);

        Exchange listIndices = new DefaultExchange(context);
        listIndices.getMessage().setHeader(NitriteConstants.OPERATION, new ListIndicesOperation());
        template.send(String.format("nitrite://%s?collection=collection", tempDb()), listIndices);

        Assert.assertEquals(0, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void getAttributesOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new GetAttributesOperation());

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertNotNull(exchange.getMessage().getBody(Attributes.class));
    }

    @Test
    public void getByIdOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new GetByIdOperation(NitriteId.createId(1L)));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertNotNull(exchange.getMessage().getBody(Document.class));
    }

    @Test
    public void insertOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new InsertOperation(
                Document.createDocument("a", "b")
        ));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void listIndicesOperation() throws Exception {
        createIndexOperation();

        Exchange listIndices = new DefaultExchange(context);
        listIndices.getMessage().setHeader(NitriteConstants.OPERATION, new ListIndicesOperation());
        template.send(String.format("nitrite://%s?collection=collection", tempDb()), listIndices);

        Assert.assertEquals(1, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void rebuildIndexOperation() throws Exception {
        createIndexOperation();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new RebuildIndexOperation(
                "key3"
        ));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);

    }

    @Test
    public void updateOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpdateOperation(
                Document.createDocument("a", "b").put("_id", 1L)
        ));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void upsertOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpsertOperation(
                Document.createDocument("a", "b").put("_id", 1L)
        ));

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void importDatabaseOperation() throws Exception {
        FileUtil.deleteFile(new File(tempDb() + "clone"));
        byte[] ddl = template.requestBodyAndHeader(
                String.format("nitrite://%s?collection=collection", tempDb()),
                null,
                NitriteConstants.OPERATION, new ExportDatabaseOperation(),
                byte[].class
        );

        Assert.assertNotNull(ddl);

        template.sendBodyAndHeader(
                String.format("nitrite://%s?collection=collection", tempDb() + "clone"),
                ddl,
                NitriteConstants.OPERATION, new ImportDatabaseOperation()
        );

        Assert.assertEquals(3,
                template.requestBodyAndHeader(
                        String.format("nitrite://%s?collection=collection", tempDb() + "clone"),
                        null,
                        NitriteConstants.OPERATION, new FindCollectionOperation(),
                        List.class
                        ).size()
        );
    }

    @Test
    public void testForbiddenOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new FindRepositoryOperation());

        template.send(String.format("nitrite://%s?collection=collection", tempDb()), exchange);

        Assert.assertTrue(
                String.format("Expected exception of type IllegalArgumentException, %s given", exchange.getException()),
                exchange.getException() instanceof IllegalArgumentException
        );
    }
}
