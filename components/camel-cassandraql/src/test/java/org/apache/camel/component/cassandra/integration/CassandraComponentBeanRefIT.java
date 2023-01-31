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

import com.datastax.oss.driver.api.core.CqlSession;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.CassandraEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CassandraComponentBeanRefIT extends BaseCassandra {

    public static final String CQL = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    public static final String SESSION_URI = "cql:bean:cassandraSession?cql=" + CQL;

    @BindToRegistry("cassandraSession")
    private CqlSession session = getSession();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:inputSession").to(SESSION_URI);
            }
        };
    }

    @Test
    public void testSession() {
        CassandraEndpoint endpoint = getMandatoryEndpoint(SESSION_URI, CassandraEndpoint.class);

        assertEquals(KEYSPACE_NAME, endpoint.getKeyspace());
        assertEquals(CQL, endpoint.getCql());
    }
}
