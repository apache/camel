/**
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

import java.util.Iterator;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.hamcrest.CoreMatchers.instanceOf;

public class SqlConsumerOutputTypeStreamListTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testReturnAnIterator() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        context.startRoute("route1");

        mock.assertIsSatisfied();
        assertThat(resultBodyAt(mock, 0), instanceOf(Iterator.class));
    }

    @Test
    public void testSplit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        context.startRoute("route2");

        mock.assertIsSatisfied();
        assertThat(resultBodyAt(mock, 0), instanceOf(Map.class));
        assertThat(resultBodyAt(mock, 1), instanceOf(Map.class));
        assertThat(resultBodyAt(mock, 2), instanceOf(Map.class));
    }

    @Test
    public void testSplitWithModel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        context.startRoute("route3");

        mock.assertIsSatisfied();
        assertThat(resultBodyAt(mock, 0), instanceOf(ProjectModel.class));
        assertThat(resultBodyAt(mock, 1), instanceOf(ProjectModel.class));
        assertThat(resultBodyAt(mock, 2), instanceOf(ProjectModel.class));
    }

    private Object resultBodyAt(MockEndpoint result, int index) {
        return result.assertExchangeReceived(index).getIn().getBody();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("sql:select * from projects order by id?outputType=StreamList&consumer.initialDelay=0&consumer.delay=50").routeId("route1").noAutoStartup()
                        .to("log:stream")
                        .to("mock:result");

                from("sql:select * from projects order by id?outputType=StreamList&consumer.initialDelay=0&consumer.delay=50").routeId("route2").noAutoStartup()
                        .to("log:stream")
                        .split(body()).streaming()
                            .to("log:row")
                            .to("mock:result")
                        .end();

                from("sql:select * from projects order by id?outputType=StreamList&outputClass=org.apache.camel.component.sql.ProjectModel&consumer.initialDelay=0&consumer.delay=50")
                        .routeId("route3").noAutoStartup()
                        .to("log:stream")
                        .split(body()).streaming()
                            .to("log:row")
                            .to("mock:result")
                        .end();
            }
        };
    }
}
