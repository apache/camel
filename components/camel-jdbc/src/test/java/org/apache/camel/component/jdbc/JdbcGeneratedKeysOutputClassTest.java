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
package org.apache.camel.component.jdbc;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The generated keys are maps of the key columns, whatever the outputClass and readSize of the endpoint.
 */
public class JdbcGeneratedKeysOutputClassTest extends AbstractJdbcTestSupport {

    @Test
    public void testGeneratedKeysAreMaps() {
        Exchange out = template.request("direct:insert", e -> {
            e.getIn().setBody("insert into tableWithAutoIncr (content) values ('value2')");
            e.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        });

        List<?> keys = out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, List.class);
        assertEquals(1, keys.size());
        Map<?, ?> row = assertInstanceOf(Map.class, keys.get(0));
        assertEquals(2, ((Number) row.get("ID")).intValue());
        assertEquals(1, out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT));
    }

    @Test
    public void testAllGeneratedKeysWithReadSize() {
        Exchange out = template.request("direct:insert", e -> {
            e.getIn().setBody("insert into tableWithAutoIncr (content) values ('value2'), ('value3')");
            e.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        });

        List<?> keys = out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, List.class);
        assertEquals(2, keys.size());
        assertEquals(2, out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("jdbc:testdb?readSize=1&outputClass=org.apache.camel.component.jdbc.CustomerModel");
            }
        };
    }
}
