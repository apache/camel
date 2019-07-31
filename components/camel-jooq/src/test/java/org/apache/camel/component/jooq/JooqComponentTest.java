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
package org.apache.camel.component.jooq;

import org.apache.camel.PropertyBindingException;
import org.apache.camel.component.jooq.db.tables.records.BookStoreRecord;
import org.junit.Test;

public class JooqComponentTest extends BaseJooqTest {

    @Test
    public void testEndpointConfiguration() throws Exception {
        JooqComponent component = (JooqComponent) context().getComponent("jooq");

        JooqEndpoint ep1 = (JooqEndpoint) component.createEndpoint("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord");
        assertEquals(JooqOperation.NONE, ep1.getConfiguration().getOperation());
        assertEquals(BookStoreRecord.class, ep1.getConfiguration().getEntityType());

        JooqEndpoint ep2 = (JooqEndpoint) component.createEndpoint("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=execute");
        assertEquals(JooqOperation.EXECUTE, ep2.getConfiguration().getOperation());
        assertEquals(BookStoreRecord.class, ep2.getConfiguration().getEntityType());

        JooqEndpoint ep3 = (JooqEndpoint) component.createEndpoint("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=fetch");
        assertEquals(JooqOperation.FETCH, ep3.getConfiguration().getOperation());
        assertEquals(BookStoreRecord.class, ep3.getConfiguration().getEntityType());
    }

    @Test(expected = PropertyBindingException.class)
    public void testNonDefaultConfig() throws Exception {
        JooqComponent component = (JooqComponent) context().getComponent("jooq");
        component.createEndpoint("jooq://org.apache.camel.component.jooq.db.tables.records.BookStoreRecord?operation=unexpectedOperation");
    }
}
