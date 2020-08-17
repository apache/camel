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
package org.apache.camel.component.elsql;

import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ElSqlDataSourceTest extends CamelTestSupport {

    @BindToRegistry("dataSource")
    private EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.DERBY)
            .addScript("sql/createAndPopulateDatabase.sql").build();

    @Test
    void testSimpleBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:simple", "XXX");

        mock.assertIsSatisfied();

        // the result is a List
        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());

        // and each row in the list is a Map
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));

        // and we should be able the get the project from the map that should be
        // Linux
        assertEquals("Linux", row.get("PROJECT"));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:simple").to("elsql:projectsById:elsql/projects.elsql?dataSource=#dataSource").to("mock:result");
            }
        };
    }
}
