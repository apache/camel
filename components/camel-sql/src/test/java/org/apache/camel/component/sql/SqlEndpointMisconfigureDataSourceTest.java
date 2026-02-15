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
package org.apache.camel.component.sql;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlEndpointMisconfigureDataSourceTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Test
    public void testFail() {
        context.getRegistry().bind("myDataSource", db);

        RouteBuilder rb = new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("sql:foo?dataSource=myDataSource")
                        .to("mock:result");
            }
        };

        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, () -> context.addRoutes(rb),
                "Should throw exception");

        PropertyBindingException pbe = (PropertyBindingException) e.getCause().getCause();
        assertEquals("dataSource", pbe.getPropertyName());
        assertEquals("myDataSource", pbe.getValue());
    }

    @Test
    public void testOk() throws Exception {
        context.getRegistry().bind("myDataSource", db);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("sql:foo?dataSource=#myDataSource")
                        .to("mock:result");
            }
        });
        assertDoesNotThrow(() -> context.start());
    }

    @Override

    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();

    }

    @Override
    public void doPostTearDown() throws Exception {

        if (db != null) {
            db.shutdown();
        }
    }

}
