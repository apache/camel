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
import org.apache.camel.component.nitrite.operation.common.InsertOperation;
import org.apache.camel.component.nitrite.operation.repository.FindRepositoryOperation;
import org.apache.camel.component.nitrite.operation.repository.RemoveRepositoryOperation;
import org.dizitart.no2.event.ChangeType;
import org.dizitart.no2.objects.filters.ObjectFilters;
import org.junit.Assert;
import org.junit.Test;

public class NitriteConsumerRepositoryTest extends AbstractNitriteTest {

    @Test
    public void testRepositoryUpsertInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(1L, "val1", "val2", "")
        );
        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(2L, "val3", "val4", "")
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
        Assert.assertEquals("val1", change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertNotNull(change2.getMessage().getBody(Map.class).get("key2"));
        Assert.assertEquals("val4", change2.getMessage().getBody(Map.class).get("key2"));
    }

    @Test
    public void testRepositoryUpsertUpdate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(123L, "val1", "val2", "")
        );
        template.sendBody(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(123L, "val3", "val4", "")
        );

        mock.assertIsSatisfied();

        Assert.assertEquals(
                1,
                template.requestBody("direct:listAll", null, List.class).size()
        );

        List<Exchange> sorted = sortByChangeTimestamp(mock.getExchanges());
        Exchange change1 = sorted.get(0);
        Exchange change2 = sorted.get(1);

        Assert.assertEquals(ChangeType.INSERT, change1.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));
        Assert.assertEquals(ChangeType.UPDATE, change2.getMessage().getHeader(NitriteConstants.CHANGE_TYPE));

        Assert.assertNotNull(change1.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));
        Assert.assertNotNull(change2.getMessage().getHeader(NitriteConstants.CHANGE_TIMESTAMP));

        Assert.assertNotNull(change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertEquals("val1", change1.getMessage().getBody(Map.class).get("key1"));
        Assert.assertNotNull(change2.getMessage().getBody(Map.class).get("key2"));
        Assert.assertEquals("val4", change2.getMessage().getBody(Map.class).get("key2"));
    }

    @Test
    public void testRepositoryRemove() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        template.sendBodyAndHeader(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                new MyPersistentObject(123L, "val1", "val2", ""),
                NitriteConstants.OPERATION, new InsertOperation()
        );
        template.sendBodyAndHeader(String.format("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName()),
                null,
                NitriteConstants.OPERATION, new RemoveRepositoryOperation(ObjectFilters.eq("key1", "val1"))
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
                fromF("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName())
                        .to("mock:result");

                from("direct:listAll")
                        .setHeader(NitriteConstants.OPERATION, constant(new FindRepositoryOperation()))
                        .toF("nitrite://%s?repositoryClass=%s", tempDb(), MyPersistentObject.class.getCanonicalName());
            }
        };
    }
}
