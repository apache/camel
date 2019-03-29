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

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlProducerOutputTypeSelectOneTest {

    private EmbeddedDatabase db;
    private DefaultCamelContext camel1;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.getComponent("sql", SqlComponent.class).setDataSource(db);
    }

    @After
    public void tearDown() throws Exception {
        camel1.stop();
        db.shutdown();
    }

    @Test
    public void testSelectOneWithClass() throws Exception {
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("sql:select * from projects where id=3?outputType=SelectOne&outputClass=org.apache.camel.component.sql.ProjectModel")
                        .to("mock:result");
            }
        });
        camel1.start();

        ProducerTemplate template = camel1.createProducerTemplate();

        MockEndpoint mock = (MockEndpoint) camel1.getEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "testmsg");

        mock.assertIsSatisfied(2000);

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Assert.assertThat(exchanges.size(), CoreMatchers.is(1));

        ProjectModel result = exchanges.get(0).getIn().getBody(ProjectModel.class);
        Assert.assertThat(result.getId(), CoreMatchers.is(3));
        Assert.assertThat(result.getProject(), CoreMatchers.is("Linux"));
        Assert.assertThat(result.getLicense(), CoreMatchers.is("XXX"));
    }

    @Test
    public void testSelectOneWithoutClass() throws Exception {
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("sql:select * from projects where id=3?outputType=SelectOne")
                        .to("mock:result");
            }
        });
        camel1.start();

        ProducerTemplate template = camel1.createProducerTemplate();

        MockEndpoint mock = (MockEndpoint) camel1.getEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "testmsg");

        mock.assertIsSatisfied(2000);

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Assert.assertThat(exchanges.size(), CoreMatchers.is(1));

        Map<String, Object> result = exchanges.get(0).getIn().getBody(Map.class);
        Assert.assertThat((Integer) result.get("ID"), CoreMatchers.is(3));
        Assert.assertThat((String) result.get("PROJECT"), CoreMatchers.is("Linux"));
        Assert.assertThat((String) result.get("LICENSE"), CoreMatchers.is("XXX"));
    }

    @Test
    public void testSelectOneSingleColumn() throws Exception {
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("sql:select project from projects where id=3?outputType=SelectOne")
                        .to("mock:result");
            }
        });
        camel1.start();

        ProducerTemplate template = camel1.createProducerTemplate();

        MockEndpoint mock = (MockEndpoint) camel1.getEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "testmsg");

        mock.assertIsSatisfied(2000);

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Assert.assertThat(exchanges.size(), CoreMatchers.is(1));

        String result = exchanges.get(0).getIn().getBody(String.class);
        Assert.assertThat(result, CoreMatchers.is("Linux"));
    }

    @Test
    public void testSelectOneSingleColumnCount() throws Exception {
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("sql:select count(*) from projects?outputType=SelectOne")
                        .to("mock:result");
            }
        });
        camel1.start();

        ProducerTemplate template = camel1.createProducerTemplate();

        MockEndpoint mock = (MockEndpoint) camel1.getEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "testmsg");

        mock.assertIsSatisfied(2000);

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Assert.assertThat(exchanges.size(), CoreMatchers.is(1));

        Long result = exchanges.get(0).getIn().getBody(Long.class);
        Assert.assertThat(result, CoreMatchers.is(3L));
    }
}
