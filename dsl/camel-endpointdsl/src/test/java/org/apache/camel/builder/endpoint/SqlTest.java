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
package org.apache.camel.builder.endpoint;

import javax.sql.DataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.dsl.SqlEndpointBuilderFactory;
import org.apache.camel.component.sql.SqlEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.*;

public class SqlTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSqlDataSourceInstance() throws Exception {
        context.start();

        final DataSource ds = new SimpleDriverDataSource();
        context.getRegistry().bind("myDS", ds);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                SqlEndpointBuilderFactory.SqlEndpointBuilder builder
                        = sql("SELECT * FROM FOO").dataSource(ds);
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                SqlEndpoint se = assertIsInstanceOf(SqlEndpoint.class, endpoint);
                assertEquals("SELECT * FROM FOO", se.getQuery());
                assertSame(ds, se.getDataSource());
            }
        });

        context.stop();
    }

    @Test
    public void testSqlDataSourceRefSyntax() throws Exception {
        context.start();

        final DataSource ds = new SimpleDriverDataSource();
        context.getRegistry().bind("myDS", ds);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                SqlEndpointBuilderFactory.SqlEndpointBuilder builder
                        = sql("SELECT * FROM FOO").dataSource("#myDS");
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                SqlEndpoint se = assertIsInstanceOf(SqlEndpoint.class, endpoint);
                assertEquals("SELECT * FROM FOO", se.getQuery());
                assertSame(ds, se.getDataSource());
            }
        });

        context.stop();
    }

    @Test
    public void testSqlDataSourceRefBeanSyntax() throws Exception {
        context.start();

        final DataSource ds = new SimpleDriverDataSource();
        context.getRegistry().bind("myDS", ds);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                SqlEndpointBuilderFactory.SqlEndpointBuilder builder
                        = sql("SELECT * FROM FOO").dataSource("#bean:myDS");
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                SqlEndpoint se = assertIsInstanceOf(SqlEndpoint.class, endpoint);
                assertEquals("SELECT * FROM FOO", se.getQuery());
                assertSame(ds, se.getDataSource());
            }
        });

        context.stop();
    }

    @Test
    public void testSqlDataSourceType() throws Exception {
        context.start();

        final DataSource ds = new SimpleDriverDataSource();
        context.getRegistry().bind("myDS", ds);

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                SqlEndpointBuilderFactory.SqlEndpointBuilder builder
                        = sql("SELECT * FROM FOO").dataSource("#type:javax.sql.DataSource");
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                SqlEndpoint se = assertIsInstanceOf(SqlEndpoint.class, endpoint);
                assertEquals("SELECT * FROM FOO", se.getQuery());
                assertSame(ds, se.getDataSource());
            }
        });

        context.stop();
    }

}
