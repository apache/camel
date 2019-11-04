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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.operation.collection.FindCollectionOperation;
import org.apache.camel.component.nitrite.operation.common.CreateIndexOperation;
import org.apache.camel.component.nitrite.operation.common.DropIndexOperation;
import org.apache.camel.component.nitrite.operation.common.GetAttributesOperation;
import org.apache.camel.component.nitrite.operation.common.InsertOperation;
import org.apache.camel.component.nitrite.operation.common.ListIndicesOperation;
import org.apache.camel.component.nitrite.operation.common.RebuildIndexOperation;
import org.apache.camel.component.nitrite.operation.common.UpdateOperation;
import org.apache.camel.component.nitrite.operation.common.UpsertOperation;
import org.apache.camel.component.nitrite.operation.repository.FindRepositoryOperation;
import org.apache.camel.component.nitrite.operation.repository.RemoveRepositoryOperation;
import org.apache.camel.component.nitrite.operation.repository.UpdateRepositoryOperation;
import org.apache.camel.support.DefaultExchange;
import org.dizitart.no2.IndexOptions;
import org.dizitart.no2.IndexType;
import org.dizitart.no2.WriteResult;
import org.dizitart.no2.meta.Attributes;
import org.dizitart.no2.objects.filters.ObjectFilters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NitriteProducerRepositoryTest extends AbstractNitriteTest {

    @Before
    public void insertData() {
        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(1, "a", "b", "")
        );
        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(2, "c", "d", "")
        );
        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(3, "e", "f", "")
        );
    }

    @Test
    public void findRepositoryOperation() throws Exception {
        List<MyPersistentObject> result = template.requestBodyAndHeader(
                String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                null,
                NitriteConstants.OPERATION, new FindRepositoryOperation(ObjectFilters.eq("key1", "c")),
                List.class
        );

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("c", result.get(0).getKey1());
        Assert.assertEquals("d", result.get(0).getKey2());
    }

    @Test
    public void removeRepositoryOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new RemoveRepositoryOperation(ObjectFilters.eq("key2", "b")));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void updateRepositoryOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpdateRepositoryOperation(ObjectFilters.eq("key2", "f")));
        exchange.getMessage().setBody(new MyPersistentObject(3, "updatedA", "updatedB", ""));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void createIndexOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new CreateIndexOperation("key3", IndexOptions.indexOptions(IndexType.Unique)));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);

        Exchange listIndices = new DefaultExchange(context);
        listIndices.getMessage().setHeader(NitriteConstants.OPERATION, new ListIndicesOperation());
        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), listIndices);

        Assert.assertEquals(4, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void dropIndexOperation() throws Exception {
        createIndexOperation();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new DropIndexOperation("key3"));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);

        Exchange listIndices = new DefaultExchange(context);
        listIndices.getMessage().setHeader(NitriteConstants.OPERATION, new ListIndicesOperation());
        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), listIndices);

        Assert.assertEquals(3, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void getAttributesOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new GetAttributesOperation());

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
        Assert.assertNotNull(exchange.getMessage().getBody(Attributes.class));
    }

    @Test
    public void insertOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new InsertOperation(
                new MyPersistentObject(123, "", "", "")
        ));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
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
        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), listIndices);

        Assert.assertEquals(4, listIndices.getMessage().getBody(List.class).size());
    }

    @Test
    public void rebuildIndexOperation() throws Exception {
        createIndexOperation();
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new RebuildIndexOperation(
                "key3"
        ));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);

    }

    @Test
    public void updateOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpdateOperation(
                new MyPersistentObject(1, "", "", "")
        ));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void upsertOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new UpsertOperation(
                new MyPersistentObject(1, "", "", "")
        ));

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);
        Assert.assertEquals(
                1,
                exchange.getMessage().getHeader(NitriteConstants.WRITE_RESULT, WriteResult.class).getAffectedCount()
        );
    }

    @Test
    public void testForbiddenOperation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(NitriteConstants.OPERATION, new FindCollectionOperation());

        template.send(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()), exchange);

        Assert.assertTrue(
                String.format("Expected exception of type IllegalArgumentException, %s given", exchange.getException()),
                exchange.getException() instanceof IllegalArgumentException
        );
    }
}
