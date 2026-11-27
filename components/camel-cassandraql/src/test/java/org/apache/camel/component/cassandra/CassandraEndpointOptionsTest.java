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
package org.apache.camel.component.cassandra;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Endpoint options of the cql component that need no Cassandra server: the endpoint is created, but not started (a
 * started endpoint connects to the hosts).
 */
public class CassandraEndpointOptionsTest extends CamelTestSupport {

    private CassandraEndpoint createEndpoint(String uri) throws Exception {
        return (CassandraEndpoint) context.getComponent("cql", CassandraComponent.class).createEndpoint(uri);
    }

    @Test
    public void testLowerCaseLimitConversionStrategy() throws Exception {
        CassandraEndpoint endpoint = createEndpoint("cql:localhost/camel_ks?resultSetConversionStrategy=limit_10");

        ResultSet resultSet = mock(ResultSet.class);
        List<Row> rows = Collections.nCopies(20, mock(Row.class));
        when(resultSet.iterator()).thenReturn(rows.iterator());

        Object body = endpoint.getResultSetConversionStrategy().getBody(resultSet);
        assertEquals(10, ((List<?>) body).size());
    }

    @Test
    public void testUnknownExtraTypeCodecFails() throws Exception {
        CassandraEndpoint endpoint = createEndpoint("cql:localhost/camel_ks?extraTypeCodecs=BLOB_TO_ARRAY,BLOB_TO_ARAY");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::createSessionBuilder);
        assertTrue(e.getMessage().contains("BLOB_TO_ARAY"), e.getMessage());
        assertTrue(e.getMessage().contains("LOCAL_TIMESTAMP_UTC"), e.getMessage());
    }

    @Test
    public void testKnownExtraTypeCodecsAndEmptyNames() throws Exception {
        CassandraEndpoint endpoint = createEndpoint("cql:localhost/camel_ks?extraTypeCodecs=BLOB_TO_ARRAY, TIMESTAMP_UTC,");

        assertDoesNotThrow(endpoint::createSessionBuilder);
    }

    @Test
    public void testConsistencyLevelIsSetByTheEndpointConfigurer() throws Exception {
        CassandraEndpoint endpoint = createEndpoint("cql:localhost/camel_ks");

        // the generated configurer binds the option, without the reflection fallback of the property binding
        assertTrue(new CassandraEndpointConfigurer().configure(context, endpoint, "consistencyLevel", "QUORUM", true));
        assertEquals(DefaultConsistencyLevel.QUORUM, endpoint.getConsistencyLevel());
    }

    @Test
    public void testComponentMetadata() throws Exception {
        JsonObject json = (JsonObject) Jsoner.deserialize(
                ((CatalogCamelContext) context).getComponentParameterJsonSchema("cql"));
        Map<String, Object> properties = json.getMap("properties");
        Map<String, Object> headers = json.getMap("headers");

        Map<?, ?> consistencyLevel = (Map<?, ?>) properties.get("consistencyLevel");
        assertNotNull(consistencyLevel, "consistencyLevel is an endpoint option");
        assertTrue(((List<?>) consistencyLevel.get("enum")).contains("LOCAL_QUORUM"));
        assertEquals("ALL", ((Map<?, ?>) properties.get("resultSetConversionStrategy")).get("defaultValue"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) properties.get("clusterName")).get("deprecated"));
        assertEquals("Object", ((Map<?, ?>) headers.get(CassandraConstants.CQL_QUERY)).get("javaType"));
    }
}
