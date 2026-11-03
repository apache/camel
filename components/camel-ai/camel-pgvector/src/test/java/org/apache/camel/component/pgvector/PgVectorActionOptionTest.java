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
package org.apache.camel.component.pgvector;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action option of the endpoint selects the action, and the CamelPgVectorAction header wins over it. The DataSource
 * has no database: DROP_TABLE fails when it asks for a connection, and UPSERT before, on a body that is not the vector.
 */
public class PgVectorActionOptionTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    throw new SQLException("No database");
                });
        context.getComponent(PgVector.SCHEME, PgVectorComponent.class).getConfiguration().setDataSource(dataSource);
        return context;
    }

    @Test
    public void actionOption() {
        Exchange result = fluentTemplate.to("pgvector:items?action=DROP_TABLE")
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(SQLException.class).hasMessage("No database");
    }

    @Test
    public void headerWinsOverActionOption() {
        Exchange result = fluentTemplate.to("pgvector:items?action=DROP_TABLE")
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withBody(new Object())
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(List.class));
    }

    @Test
    public void noAction() {
        Exchange result = fluentTemplate.to("pgvector:items")
                .request(Exchange.class);

        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }
}
