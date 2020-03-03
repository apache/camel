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
package org.apache.camel.component.sql.stored;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class ProducerBatchInvalidTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        db.shutdown();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void shouldInvalid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                // batch option has been configured twice
                from("direct:query").to("sql-stored:BATCHFN(INTEGER :#num)?batch=true&batch=true").to("mock:query");
            }
        });
        try {
            context.start();
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            ResolveEndpointFailedException refe = assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
            PropertyBindingException pbe = assertIsInstanceOf(PropertyBindingException.class, refe.getCause());
            assertEquals("batch", pbe.getPropertyName());
            assertIsInstanceOf(TypeConversionException.class, pbe.getCause());
            assertEquals("[true, true]", pbe.getValue().toString());
        }
    }

}
