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
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nitrite.operation.collection.FindCollectionOperation;
import org.apache.camel.component.nitrite.operation.collection.RemoveCollectionOperation;
import org.apache.camel.component.nitrite.operation.common.InsertOperation;
import org.dizitart.no2.Document;
import org.dizitart.no2.event.ChangeType;
import org.dizitart.no2.filters.Filters;
import org.junit.Assert;
import org.junit.Test;

public class NitriteConsumerCollectionTest extends AbstractNitriteTest {

    @Test
    public void testCollectionUpsertInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1")
        );
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key2", "value2")
        );

        mock.assertIsSatisfied();

        List<Exchange> sorted = sortByChangeTimestamp(mock.getExchanges());
        Exchange change1 = sorted.get(0);
        Exchange change2 = sorted.get(1);

        Assert.assertEquals(ChangeType.INSERT, change1.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));
        Assert.assertEquals(ChangeType.INSERT, change2.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));

        Assert.assertNotNull(change1.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));
        Assert.assertNotNull(change2.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));

        Assert.assertNotNull(change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertEquals("value1", change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertNotNull(change2.getMessage().getBody(Map.class).get("key2"));
        Assert.assertEquals("value2", change2.getMessage().getBody(Map.class).get("key2"));
    }

    @Test
    public void testCollectionUpsertUpdate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1").put("_id", 123L)
        );
        template.sendBody(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key2", "value2").put("_id", 123L)
        );
        mock.assertIsSatisfied();

        List<Exchange> sorted = sortByChangeTimestamp(mock.getExchanges());
        Exchange change1 = sorted.get(0);
        Exchange change2 = sorted.get(1);

        Assert.assertEquals(ChangeType.INSERT, change1.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));
        Assert.assertEquals(ChangeType.UPDATE, change2.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));

        Assert.assertNotNull(change1.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));
        Assert.assertNotNull(change2.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));

        Assert.assertNotNull(change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertEquals("value1", change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertNotNull(change2.getMessage().getBody(Map.class).get("key2"));
        Assert.assertEquals("value2", change2.getMessage().getBody(Map.class).get("key2"));
    }

    @Test
    public void testCollectionInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBodyAndHeader(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1"),
                NitriteConstants.OPERATION, new InsertOperation()
        );

        mock.assertIsSatisfied();

        Exchange change1 = mock.getExchanges().get(0);

        Assert.assertEquals(ChangeType.INSERT, change1.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));

        Assert.assertNotNull(change1.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));

        Assert.assertNotNull(change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertEquals("value1", change1.getMessage().getBody(Map.class).get("key1"));
    }

    @Test
    public void testCollectionRemove() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBodyAndHeader(String.format("nitrite://%s?collection=collection", tempDb()),
                Document.createDocument("key1", "value1"),
                NitriteConstants.OPERATION, new InsertOperation()
        );
        template.sendBodyAndHeader(String.format("nitrite://%s?collection=collection", tempDb()),
                null,
                NitriteConstants.OPERATION, new RemoveCollectionOperation(Filters.eq("key1", "value1"))
        );

        Assert.assertEquals(
                0,
                template.requestBody("direct:listAll", null, List.class).size()
        );

        mock.assertIsSatisfied();

        List<Exchange> sorted = sortByChangeTimestamp(mock.getExchanges());
        Exchange insert = sorted.get(0);
        Exchange remove = sorted.get(1);

        Assert.assertEquals(ChangeType.INSERT, insert.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));
        Assert.assertEquals(ChangeType.REMOVE, remove.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));

        Assert.assertNotNull(insert.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));
        Assert.assertNotNull(remove.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("nitrite://%s?collection=collection", tempDb())
                        .to("mock:result");

                from("direct:listAll")
                        .setHeader(NitriteConstants.OPERATION, constant(new FindCollectionOperation()))
                        .toF("nitrite://%s?collection=collection", tempDb());
            }
        };
    }
}
