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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class ElSqlProducerBatchTest extends CamelTestSupport {

    @BindToRegistry("dataSource")
    private EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.DERBY)
            .addScript("sql/createAndPopulateDatabase.sql").build();

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    void testBatchMode() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(SqlConstants.SQL_UPDATE_COUNT).isEqualTo(1);

        Map<String, Object> batchParams = new HashMap<>();
        batchParams.put("id", "4");
        batchParams.put("license", "GNU");
        batchParams.put("project", "Batch");

        template.sendBody("direct:batch", batchParams);

        mock.assertIsSatisfied();
    }

    @Test
    void testNonBatchMode() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(SqlConstants.SQL_UPDATE_COUNT).isEqualTo(1);
        mock.message(0).header("id").isEqualTo("4");
        mock.message(0).header("license").isEqualTo("GNU");
        mock.message(0).header("project").isEqualTo("nonBatch");

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", "4");
        headers.put("license", "GNU");
        headers.put("project", "nonBatch");

        template.sendBodyAndHeaders("direct:nonBatch", "", headers);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:batch").to("elsql:insertProject:elsql/projects.elsql?dataSource=#dataSource&batch=true")
                        .to("mock:result");

                from("direct:nonBatch").to("elsql:insertProject:elsql/projects.elsql?dataSource=#dataSource").to("mock:result");

            }
        };
    }

}
