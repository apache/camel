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
package org.apache.camel.component.cassandra.integration;

import java.util.LinkedHashMap;
import java.util.Map;

import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.CassandraConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Map body binds the named markers (such as :login) of the CQL statement by name.
 */
public class CassandraComponentProducerNamedIT extends BaseCassandra {

    static final String CQL = "insert into camel_user(login, first_name, last_name) values (:login, :firstName, :lastName)";

    @Produce("direct:prepared")
    ProducerTemplate prepared;

    @Produce("direct:unprepared")
    ProducerTemplate unprepared;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:prepared").toF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL);
                from("direct:unprepared")
                        .toF("cql://%s/%s?cql=%s&prepareStatements=false", getUrl(), KEYSPACE_NAME, CQL);
            }
        };
    }

    private static Map<String, Object> user(String login, String firstName, String lastName) {
        // not in the order of the markers, and with a key the statement does not use
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lastName", lastName);
        body.put("city", "Rome");
        body.put("firstName", firstName);
        body.put("login", login);
        return body;
    }

    private Row findUser(String login) {
        return getSession().execute("select login, first_name, last_name from camel_user where login = ?", login).one();
    }

    @Test
    public void testPreparedBindsByName() {
        prepared.requestBody(user("w_jiang", "Willem", "Jiang"));

        Row row = findUser("w_jiang");
        assertNotNull(row);
        assertEquals("Willem", row.getString("first_name"));
        assertEquals("Jiang", row.getString("last_name"));
    }

    @Test
    public void testUnpreparedBindsByName() {
        unprepared.requestBody(user("j_strachan", "James", "Strachan"));

        Row row = findUser("j_strachan");
        assertNotNull(row);
        assertEquals("James", row.getString("first_name"));
        assertEquals("Strachan", row.getString("last_name"));
    }

    @Test
    public void testPreparedMessageCqlBindsByName() {
        prepared.requestBodyAndHeader(Map.of("login", "c_ibsen", "first", "Claus 2", "last", "Ibsen 2"),
                CassandraConstants.CQL_QUERY, "update camel_user set first_name=:first, last_name=:last where login=:login");

        Row row = findUser("c_ibsen");
        assertNotNull(row);
        assertEquals("Claus 2", row.getString("first_name"));
        assertEquals("Ibsen 2", row.getString("last_name"));
    }

    @Test
    public void testPreparedMissingValueNamesTheMarker() {
        Map<String, Object> body = Map.of("login", "g_nodet", "firstName", "Guillaume");

        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> prepared.requestBody(body));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains(":lastname"), cause.getMessage());
        assertNull(findUser("g_nodet"));
    }
}
